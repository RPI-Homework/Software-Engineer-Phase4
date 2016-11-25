
import soot.*;
import soot.util.*;
import soot.jimple.*;
import java.util.*;

public class ChaAnalysis {

    // ----------------------------------------------
    // a SootMethod object representing method "main"
    private SootMethod main_method;

    // initialize main_method
    public void setMainClass(String main_class_name) {
	
	// lookup the SootClass object representing the Java class
	// containing the main method of the analyzed program
	SootClass main_class = Scene.v().getSootClass(main_class_name);
	
	// lookup the main method inside the class, using the signature
	main_method = main_class.getMethod("void main(java.lang.String[])");
    }

    // --------------------------------------------------------- 
    // this object is responsible for writing to the appropriate
    // output files. it will be invoked periodically by ChaAnalysis.
    private ChaWriter writer;

    // create and initialize the writer
    public void setOutputDir(String dir_name) {
	writer = new ChaWriter(dir_name,hierarchy);
    }
    
    // ----------------------------------------------------------------
    // this object stores information about the class hierarchy, to be
    // used throughout the analysis
    private Hierarchy hierarchy = new Hierarchy();

    // ---------------------------------------------------------
    // worklist (which is basically a queue) that stores reachable
    // methods whose bodies have to be processed. this workist is used
    // during the call graph construction, similarly to what happens
    // in CHA as we discussed in class. implemented by a standard
    // library class java.util.ArrayList. 
    private ArrayList worklist = new ArrayList(); 

    // ----------------------------------------------------------

    // For each method M that is determined to be reachable, table
    // reachable_methods contains the pair (M,id), where id is a
    // unique integer identifier for M. There ids are written to file
    // rmethods, and are later used for the instrumentation. Since we
    // don't care about instrumenting library methods, all such
    // methods are assigned id = 0. 
    private Hashtable reachable_methods = new Hashtable();

    // this is a counter used to assign ids to the reachable
    // non-library methods
    private int method_id = 1;

    // --------------------------------------------------------------
    // a helper method for adding a newly-discovered reachable method
    // to the end of the worklist. this schedules the method for
    // processing in the future.
    private void addToWorklist(SootMethod m) {

	// we only add methods that haven't been discovered yet
	if (reachable_methods.containsKey(m)) return;

	// add at the end of the worklist
	worklist.add(m);
	
	// debugging print
	// System.out.println("- Added " + m);

	// add to the set of reachable methods.
	// we only care about ids for non-library methods
	if (hierarchy.notLibrary(m))
	    reachable_methods.put(m, new Integer(method_id++));
	else
	    reachable_methods.put(m, new Integer(0));

	// Here need to take into account finalizers: if the added
	// method is a constructor, and if the class has a finalizer
	// method, this finalizer is also potentially
	// reachable. Basically, if any constructor can be called to
	// create an object, later the JVM will call the finalizer
	// when destroying the object.

	if (m.getName().equals("<init>") &&
	    m.getDeclaringClass().declaresMethod("void finalize()") ) {
	    SootMethod fnl =
		m.getDeclaringClass().getMethod("void finalize()");
	    addToWorklist(fnl);
	}
    }

    // -------------------------------------
    // the top-level control for the analysis
    public void analyze() {
	
	// the list of all classes, including library classes
	Chain allClasses = Scene.v().getApplicationClasses();

	// initialize the data structures related to the class hierarchy
	hierarchy.initialize(allClasses);

	// everything starts with "main"
	addToWorklist(main_method);

	// we also have to take into account the initialization of
	// static fields in all classes. these initializations are in
	// artificial static methods called <clinit> which are created
	// by the java compiler. there are no explicit calls to
	// <clinit>, but the JVM does invoke them whenever it loads a
	// class. for now, we will just make all <clinit> methods
	// reachable; this is overly conservative, but quite simple.
	for (Iterator it = allClasses.iterator(); it.hasNext();) {
	    SootClass c = (SootClass) it.next();
	    if (c.declaresMethod("void <clinit>()")) {
		SootMethod class_init = c.getMethod("void <clinit>()");
		addToWorklist(class_init);
            }
        }

	// once the worklist in initialized with "main" and <clinit>,
	// start processing. this implements breadth-first
	// construction of the call graph.

	while (worklist.size() != 0) {

	    // remove the 0-th element (head) of the worklist. 
	    SootMethod m = (SootMethod) worklist.remove(0);
	    
	    // process the body of the method, and find what methods
	    // it calls
	    processMethod(m);
	}

    } // end of analyze()

    // ---------------------------------------
    private void processMethod(SootMethod m) {

	// native methods and abstract methods do not have bodies
	if(m.isNative() || m.isAbstract()) return;

	// debugging print
	// System.out.println("---- Processing " + m);

	// start writing info about this method to the output files
	writer.startNewMethod(m,getMethodId(m));

	// a counter for the call sites inside the body. we need this
	// to create ids for all call sites inside non-library
	// methods. these ids are written in files "calls" and "edges"
	int site_number = 1;

	// go through all statements in the body
	for (Iterator uIt = m.getActiveBody().getUnits().iterator();
	     uIt.hasNext();) {
	 
	    // a Soot object representing a JIMPLE statement
	    Stmt s = (Stmt) uIt.next();

	    // debugging print
	    // System.out.println(s);

	    // not all statements have calls inside them. if there
	    // isn't a call, we jut go to the next statement
	    if ( ! s.containsInvokeExpr() ) continue;

	    // get a Soot object that represents the call expression
	    InvokeExpr call = (InvokeExpr) s.getInvokeExpr();

	    // create and id for the call. for the x-th call in method
	    // with method_id=y, the id is "y_x"
	    String call_site_id = getMethodId(m) + "_" + site_number;
	    processCall(call,m,call_site_id);
	    site_number++;
	}
	    
    }
    
    // ------------------------------------
    private int getMethodId(SootMethod m) {
	return ((Integer)reachable_methods.get(m)).intValue();
    }

    // -----------------------------------------------------
    private void processCall(InvokeExpr call,SootMethod m, 
			     String call_site_id) {

	// need to determine the potential run-time targets of the
	// call. for virtual calls, this is done based on the class
	// hierarchy

	// -------------------------------------------------------
	// CASE 1: staticinvoke or specialinvoke. In this case the
	// compile-time target and the run-time target are the same,
	// so we just label the compile-time target as reachable and
	// add it to the worklist
	if (call instanceof StaticInvokeExpr || 
	    call instanceof SpecialInvokeExpr) {
	    SootMethod static_target = call.getMethod();
	    addToWorklist(static_target);
	    writer.writeSimpleCall(call,call_site_id,
				   getMethodId(static_target),m);
	    return;
	}

	// ---------------------------------------------------------------
	// CASE 2: a virtual call: either virtualinvoke or interfaceinvoke
	if (call instanceof VirtualInvokeExpr ||
	    call instanceof InterfaceInvokeExpr) {

	    // the compile-time target method of the call
	    SootMethod static_target = call.getMethod();

	    // determine the expression that is used for the
	    // receiver. for example, if the call is "x.m()", we want
	    // to get access to x.
	    Value receiver_expr = ((InstanceInvokeExpr)call).getBase();

	    // the compile-time type of the receiver expression
	    Type static_type = receiver_expr.getType();

	    // don't worry about this: just a small detail related to
	    // arrays
	    if (! (static_type instanceof RefType)) return;

	    // The type corresponds to a particular class/interface in
	    // the class hierarchy. This class/interface is
	    // represented by a SootClass object.
	    SootClass static_class = ((RefType) static_type).getSootClass();

	    // At run time, the actual class of the receiver object
	    // could potentially be any non-abstract class that is a
	    // direct/indirect subclass of static_class (or it could
	    // be static_class itself). We need to consider each such
	    // possibility and determine the run-time target method
	    // that corresponds to that particular receiver class.

	    HashSet possible = hierarchy.possibleReceiverClasses(static_class);

	    // temporary set for gathering the outgoing call
	    // edges.
	    HashSet targets = new HashSet();

	    for (Iterator cIt = possible.iterator(); cIt.hasNext();) {

		SootClass runtime_class = (SootClass) cIt.next();

		// If at run time the receiver class is "runtime_class", 
		// which method will be the run-time target?
		SootMethod runtime_target =
		    hierarchy.virtualDispatch(static_target,runtime_class);

		targets.add(runtime_target);

		// at this point, we have discovered the call graph
		// edge (call,runtime_target). for the purposes of
		// this part of the project, we don't need to store
		// this edge anywhere. so, just continue with the
		// newly discovered reachable method runtime_target. 
		addToWorklist(runtime_target);
	    }

	    // write info to output file, including the # of receiver
	    // classes and the # of target methods, plus the set of
	    // all possible target methods
	    writer.writeComplexCall(call,call_site_id,
				    possible.size(),
				    targets.size());
	    for (Iterator mIt = targets.iterator(); mIt.hasNext();) {
		SootMethod t = (SootMethod) mIt.next();
		writer.writeTarget(call_site_id,t,getMethodId(t));
	    }
	    
	    // write annotated call edges: not only the call site id
	    // and the target method id, but also the class of the
	    // receiver. ChaWriter makes sure that we only print edges
	    // for which both the caller are the callee are inside the
	    // CUT
	    for (Iterator cIt = possible.iterator(); cIt.hasNext();) {
		SootClass runtime_class = (SootClass) cIt.next();
		SootMethod runtime_target =
		    hierarchy.virtualDispatch(static_target,runtime_class);
		writer.writeAnnotatedEdge(call_site_id,
					  m,runtime_target,
					  getMethodId(runtime_target),
					  runtime_class);
	    }
	    
	}
    }

    // -------------------------
    public void createOutput() {
	// write info about the class hierarchy
	writer.writeHierarchyInfo();
	
	// write all reachable methods
	writer.writeMethodInfo(reachable_methods);
	
	// close open files, etc.
	writer.done();
    }
}

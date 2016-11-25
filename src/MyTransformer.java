import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.util.*;
import java.util.*;


class MyTransformer extends BodyTransformer {

    // the name of the tracker class. we need to insert calls to
    // methods from this tracker class
    private static String tracker_name = "RuntimeTracker";

    // SootClass object representing the tracker class
    private static SootClass tracker_class;

    // the method inside the tracker class that should be invoked
    // immediately before an instrumented call site. 
    private static SootMethod before_call0;
    private static SootMethod before_call1;

    // the method in the tracker class that should be invoked at the
    // entry of each instrumented method.
    private static SootMethod method_entry;

    // some Soot-related code (since we are inheriting from Soot's
    // BodyTransformer)
    private static MyTransformer instance = new MyTransformer();
    private MyTransformer() {}
    public static MyTransformer v() { return instance; }
    public String getDeclaredOptions() { return super.getDeclaredOptions(); }

    // ------------------------------------------------
    // process a method body and insert instrumentation

    protected void internalTransform(Body body, 
				     String phaseName, 
				     Map options) {

	// the SootMethod we are currently instrumenting
	SootMethod method = body.getMethod();

	// initialize the static fields related to the tracker class
	if (tracker_class == null) {
	    tracker_class = 
	    	Scene.v().getSootClass(tracker_name);
	    before_call0 = 
	    	tracker_class.getMethod("void beforeCall(java.lang.String)");
	    before_call1 = 
			tracker_class.getMethod("void beforeCall(java.lang.String,java.lang.Object)");
	    method_entry = 
	    	tracker_class.getMethod("void methodEntry(int)");
	}

	// no instrumentation will be inserted in the body of the
	// tracker classs
	if (method.getDeclaringClass().
	    getName().equals(tracker_name)) return;

	// the id of this method, as read from "rmethods.cut"
	String method_id = (String) 
	    Instrumenter.id_info.get(method.toString());

	// for methods that are not in file rmethods.cut (i.e. methods
	// that are non-reachable or non-CUT), there is no id and we
	// shouldn't instrument
	if (method_id == null) return;

	// the number of parameters (including 'this'). we need this
	// to figure out the right place for method-entry
	// instrumentation - the first num_param JIMPLE statements
	// should be skipped [you don't really need to understand why
	// this is necessary].
	int num_param = method.getParameterCount();
	// for non-static methods, need to take into account 'this'
	if (!method.isStatic()) num_param++;

	Chain units = body.getUnits();
	Iterator stmtIt = units.snapshotIterator();
	Stmt s = null;
      
	// first, skip the initial assignments to parameters.
	for (int i = 0; i < num_param;  i++) {
	    s = (Stmt) stmtIt.next();
	    // sanity check; ignore it
	    Assert(s instanceof IdentityStmt, 
		   ("Expected IdentityStmt: " + s));
	}
	
	// another sanity check: there should be at least one
	// statement after this
	Assert(stmtIt.hasNext(),"Empty Body");

	// the first "real" statement
	s = (Stmt) stmtIt.next();
	
	// create a JIMPLE staticinvoke expression that calls
	// "methodEntry" in the tracker class. The actual parameter of
	// the call is the method id of the method whose body we are
	// currenty processing.
	int m_id = Integer.parseInt(method_id);
	StaticInvokeExpr sc = 
	    Jimple.v().newStaticInvokeExpr(method_entry,IntConstant.v(m_id));

	// insert the staticinvoke before the first real statement
	units.insertBefore(Jimple.v().newInvokeStmt(sc),s);

	// process all statements, starting with the first one.
	// insert instrumentation before each call site. 
	int call_site_id = 1;
	boolean first_iter = true;
	do {
	    // since we already have the first real statement from
	    // stmtIt, we shouldn't call stmtIt.next() during the
	    // first iteration
	    if (first_iter) first_iter = false;
	    else s = (Stmt) stmtIt.next();		

	    // instrument all calls
	    if (s.containsInvokeExpr()) { 

		InvokeExpr c = (InvokeExpr) s.getInvokeExpr();
		
		String call_id = method_id + "_" + call_site_id;

		// two cases here: virtualinvoke/interfaceinvoke and
		// staticinvoke/specialinvoke. For static/special, do
		// exactly what was done in phase 3 (as shown in the
		// code below). For virtual/interfaceinvoke, call the
		// version of beforeCall that has an extra Object
		// parameter, which is a pointer to the receiver
		// object.  For example, if we have a call site
		// "x.m()" with id "56_78", we need to insert statement
		// "beforeCall("56_78",x)". 

		// Right now, the code is correct only if (c
		// instanceof StaticInvokeExpr) or (c instanceof
		// SpecialInvokeExpr). However, if (c instanceof
		// VirtualInvokeExpr) or (c instanceof
		// InterfaceInvokeExpr), we need a different kind of
		// instrumentation, as described above. To get the
		// local variable that points to the receiver object
		// at the call site, cast c to an InstanceInvokeExpr,
		// call getBase() on it, and then cast it to Local.
		
		// YOUR CODE HERE

		if(c instanceof VirtualInvokeExpr || c instanceof InterfaceInvokeExpr)
		{
			sc = Jimple.v().newStaticInvokeExpr
	    		(before_call1,StringConstant.v(call_id),(Local)(((InstanceInvokeExpr)c).getBase()));
		}
		else
		{
			sc = Jimple.v().newStaticInvokeExpr
		    	(before_call0,StringConstant.v(call_id));
		}

		// insert the staticinvoke before the call
		units.insertBefore(Jimple.v().newInvokeStmt(sc),s);
		
		// update the counter of call sites
		call_site_id ++;

	    }	
	} while (stmtIt.hasNext());
    }

    // -------------------------------
    void Assert(boolean x, String s) {
	// there are better ways to do this
	if (!x) throw new RuntimeException(s);
    }
}

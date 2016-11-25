import soot.*;
import soot.util.*;
import java.util.*;

public class Hierarchy {

    // this hash table contains a pair (C,X) for each class C in
    // allClasses. X is a Hashset of all non-abstract classes that are
    // direct or indirect subclases of C (including C itself)
    private Hashtable tbl = new Hashtable();
    
    // all classes in the hierarchy
    public Set allClasses() { return tbl.keySet(); }

    public void initialize(Chain allClasses) {
	// constructs the necessary data structures for method
	// potentialReceiverClasses. More precisely, constructs all
	// pairs (C,X) in tbl
	
	// first, create an empty set for each class in allClasses
	for (Iterator it = allClasses.iterator(); it.hasNext();) 
	    tbl.put(it.next(),new HashSet());

	// next, for each non-abstract class C, recursively traverse
	// all superclasses of C and add C to their sets
	for (Iterator it = allClasses.iterator(); it.hasNext();) {
	    SootClass c = (SootClass) it.next();
	    if (notAbstract(c)) traverse(c,c);
	}
    }

    // recursive traversal of superclasses and superinterfaces
    private void traverse(SootClass sub, SootClass supr) {
	
	// sub is a subclass of supr
	
	// first, add sub to the set for supr
	((HashSet)tbl.get(supr)).add(sub);

	// traverse parent classes/interfaces of supr
	if (supr.hasSuperclass())
	    traverse(sub,supr.getSuperclass());
	for (Iterator it = supr.getInterfaces().iterator(); it.hasNext();)
	    traverse(sub,(SootClass) it.next());
    }

    public HashSet possibleReceiverClasses(SootClass static_class) {

	// this method answers the following quesiton: if the
	// compile-time receiver class is static_class, what could be
	// the potential run-time receiver classes? the method returns
	// the set of all non-abstract classes that are direct or
	// indirect subclasses of static_class.  if static_class is a
	// non-abstract class, it is also included in the set. keep in
	// mind that static_class could represent an interface; in
	// this case we need to return all non-abstract classes that
	// implement that interface - directly or indirectly through
	// superclasses. 

	return (HashSet)tbl.get(static_class);
    }

    // this method simulates the effects of the virtual dispatch
    // performed by the JVM at run time. 
    public SootMethod virtualDispatch(SootMethod static_target,
				      SootClass receiver_class) {
	SootClass curr = receiver_class;

	// System.out.println(receiver_class + " + " + 
	//                    static_target.getSubSignature());

	while (curr != null) {
	    
	    if (curr.declaresMethod((static_target.getSubSignature())))
		return curr.getMethod(static_target.getSubSignature());

	    if (curr.hasSuperclass())
		curr = curr.getSuperclass();
	    else
		curr = null;
	}

	return null; // this should never happen
    }

    public boolean notAbstract(SootClass c) {
	return  ! ( c.isInterface() || 
		    Modifier.isAbstract(c.getModifiers()));
    }

    public boolean notLibrary(SootClass c) {
	String n = c.getName();
	return ! (n.startsWith("java.") || 
		  n.startsWith("javax.") ||
		  n.startsWith("sun."));
    }

    public boolean notLibrary(SootMethod m) {
	return notLibrary(m.getDeclaringClass());
    }
}

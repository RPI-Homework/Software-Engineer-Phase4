// Don't try to understand what is going on in class Loader.  Focus on
// ChaAnalysis.

import soot.*;
import soot.util.*;
import soot.jimple.*;
import java.util.*;
import java.io.*;

class Loader {
    
    public void loadJimple(String[] args) {

	// this is trying to simulate soot.Main
	soot.Main.setReservedNames();
	Scene.v().setSootClassPath(args[0]);

	//just for debugging
	//	soot.Main.setVerbose(true);

	System.out.print("Loading [takes about a minute] ... ");
	System.out.flush();

	// Load necessary classes reachable from the main class
	SootClass c = Scene.v().loadClassAndSupport(args[1]);
	Scene.v().setMainClass(c);
	c.setApplicationClass();

	// also take care of unreachable CUT classes
	// loadAllCUT(args[2]);
	
	// NEED TO TAKE CARE OF: JVM startup classes, reflection,
	// class loaders, etc. FOR THE INITIAL PHASES OF THE PROJECT,
	// IGNORE THESE ISSUES.

	// Label all classes as application classes
	List cc = new ArrayList();
	cc.addAll(Scene.v().getContextClasses());
	for (Iterator contextClassesIt = cc.iterator();
	     contextClassesIt.hasNext();) {
	    SootClass s = (SootClass)contextClassesIt.next();
	    s.setApplicationClass();
	}

	// here Scene.v().getApplicationClasses() returns
	// all classes (app + lib).

	soot.Main.setVerbose(false);

	// Run the whole-program packs.
	Scene.v().getPack("wjtp").apply();

	// Handle each application class individually
	Iterator classIt = Scene.v().getApplicationClasses().iterator();
	while(classIt.hasNext()) {
	    SootClass s = (SootClass) classIt.next();
	    //	    System.out.println("Processing " + s.getName());
	    //	    System.out.flush();

	    // build method bodies
	    for (Iterator methodIt = s.getMethods().iterator();
		 methodIt.hasNext();) {
		SootMethod m = (SootMethod) methodIt.next();
		if (!m.isConcrete()) continue;

		// Build Jimple body and transform it.
		JimpleBody body = (JimpleBody) m.retrieveActiveBody();
		Scene.v().getPack("jtp").apply(body);

		// after this, m.hasActiveBody() returns true and
		// m.getActiveBody() works fine
            }
	}

	System.out.println("Done");

	// here the IR is created
	if (false)
	    System.out.println("\nTotal " +
			       (Scene.v().getApplicationClasses().size() +
				Scene.v().getContextClasses().size()) +
			       " classes (" +
			       Scene.v().getApplicationClasses().size() +
			       " app, " +
			       Scene.v().getContextClasses().size() +
			       " lib)\n");
    }

    // ----------------------------------------------------
    // load all CUT classes, if they are not already loaded
    public static void loadAllCUT(String dir)
    {
	String fclass = dir + "/CUT";
	try {
	    //	    	    System.out.println("\n----- Reading " + 
	    // fclass + " ------\n");
	    BufferedReader in = 
		new BufferedReader(new FileReader(fclass));
	    String line;
	    while( (line = in.readLine()) != null )
		{
		    //		    System.out.println(line);
		    if ( ! Scene.v().containsClass(line))
			Scene.v().loadClassAndSupport(line);
		}
	    in.close();
	} catch (Exception e) {
	    System.out.println("\n\n***** Problem with " + fclass + " and " + 
			       "*****\n\n");
	    System.exit(1);
	}      
    }


}


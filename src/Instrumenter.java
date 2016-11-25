import soot.*;
import java.io.*;
import java.util.*;

// this class is the top-level control for the instrumentation. It
// instantiates MyTransformer, adds it to soot, and then invokes
// soot.Main

class Instrumenter {
    
    // information about method ids, as described in file
    // "rmethods". for each line "x: YZW" in "rmethods", the table
    // contains the pair (YZW,x)
    public static Hashtable id_info = new Hashtable();

    // ----------------------------------------------------
    public static void main(String[] args) throws Exception {

	// class MyTransformer defines some code that will be executed
	// on each method body in the program. Inside MyTransformer,
	// we teak the JIMPLE for the method body and we add
	// instrumentation. The code below just "plugs" MyTransformer
	// into soot, and then invokes soot's main.

	BodyTransformer bt = MyTransformer.v();
	Scene.v().getPack("jtp").add(new Transform("jtp.cis788", bt));

	// load info about method ids from file "rmethods"
	loadInfo(args[2]);
      
	// simply call soot with the same arguments
	soot.Main.main(args);
    }

    // --------------------------------------------
    private static void loadInfo(String prog_dir) {

	// prog_dir is the directory in which the instrumented class
	// files will be stored (...../phase3/p/CLASSES). in its
	// parent directory, we have file "rmethods" that contains
	// info about methods ids.
       String parent_dir = prog_dir.substring(0,prog_dir.length() - 8);
       String f = parent_dir + "/rmethods";
       BufferedReader in;
       String line;
       
       try {
	 in = new BufferedReader(new FileReader(f));
	 while( (line = in.readLine()) != null ) {
	     int x = line.indexOf(':');
	     id_info.put(line.substring(x+2),
			 line.substring(0,x));
	 }
	     
	} catch (Exception e) {
	    // This is not the right way to deal with exceptions ...
	    System.out.println("OOPS! " + e);
	    System.exit(1);
	}

    }
}

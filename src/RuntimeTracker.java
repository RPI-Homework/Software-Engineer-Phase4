import java.io.*;
import java.util.*;
import java.util.Map.Entry;

// this class is used at run time tor gather information about methods
// and call sites, and to compute the coverage statistics.

public class RuntimeTracker {

    // directory where the output files should be written
    private static String out_dir;
        
    private static int StartEdges;
    private static int StartEdgesAnnotated;
    private static int StartMethods;
    private static TreeMap<String, Integer> edges_annotated;
    private static TreeMap<String, Integer> nedges;
    private static TreeMap<String, Integer> nedges_annotated;
    private static TreeMap<Integer, String> nmethods;

    // --------------------------------------------------------
    // before anything else in RuntimeTracker is called, method
    // 'start' should be invoked. The parameter is a directory in
    // which RuntimeTracker will find the necessary CHA-generated
    // files ("rmethods.cut" and "edges.cut"). In the same directory,
    // RuntimeTracker will write info about coverage statistics.
    public static void start(String io_dir)
    { 

		System.out.println("\n--- Instrumentation started in " + 
				   io_dir + " ---\n");
	
		out_dir = io_dir;
		nedges = new TreeMap<String, Integer>();
		edges_annotated = new TreeMap<String, Integer>();
		nedges_annotated = new TreeMap<String, Integer>();
		nmethods = new TreeMap<Integer, String>();
		
		try
		{
			//Get all rmethods
			DataInputStream stream = new DataInputStream(new FileInputStream(io_dir + "/rmethods"));
			BufferedReader file = new BufferedReader(new InputStreamReader(stream));
			
			String line;
			while ((line = file.readLine()) != null)
			{
				  int divide = line.indexOf(":");
				  int Method_ID = Integer.parseInt(line.substring(0, divide));
				  String Method = line.substring(divide+1);
				  
				  nmethods.put(Method_ID, Method);
			}
			
			stream.close();
			
			//Get all edges
			stream = new DataInputStream(new FileInputStream(io_dir + "/edges"));		
			file = new BufferedReader(new InputStreamReader(stream));
			
			while ((line = file.readLine()) != null)
			{
				  int divide = line.indexOf(",");
				  //String Call_Site_ID = line.substring(0, divide);
				  int Method_ID = Integer.parseInt(line.substring(divide+1));
				  
				  nedges.put(line, Method_ID);
			}
			
			stream.close();
			
			stream = new DataInputStream(new FileInputStream(io_dir + "/edges.annotated"));		
			file = new BufferedReader(new InputStreamReader(stream));
			
			while ((line = file.readLine()) != null)
			{
				  int divide1 = line.indexOf(",");
				  int divide2 = line.lastIndexOf(",");
				  int Method_ID = 0;
	
				  if(divide1 != divide2)
				  {
					  Method_ID = Integer.parseInt(line.substring(divide1+1,divide2));
					  edges_annotated.put(line.substring(0,divide1) + "," + line.substring(divide2+1), Method_ID);
				  }
				  else
				  {
					  Method_ID = Integer.parseInt(line.substring(divide1+1));
					  edges_annotated.put(line.substring(0,divide1), Method_ID);
				  }
				  
				  nedges_annotated.put(line, Method_ID);
			}
			
			stream.close();
		} 
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		StartEdges = nedges.size();
		StartEdgesAnnotated = nedges_annotated.size();
		StartMethods = nmethods.size();
		
    }
    
    // ---------------------------------------------------------
    // this method should be invoked at the end of the execution;
    // basically, it writes the output files to disk
    public static void end()
    {
    	System.out.println("\n--- Instrumentation ended ---\n");

		BufferedWriter nc_methods;
		
		// output file for not-covered edges
		BufferedWriter nc_edges;
		BufferedWriter nc_edges_annotated; 
	
		try
		{
		    nc_methods = 
		    	new BufferedWriter(new FileWriter(out_dir + "/nmethods"));
		    nc_edges =
		    	new BufferedWriter(new FileWriter(out_dir + "/nedges"));
		    nc_edges_annotated =
				new BufferedWriter(new FileWriter(out_dir + "/nedges.annotated"));
		    
		    for(Entry<Integer, String> iter : nmethods.entrySet())
		    {
		    	 nc_methods.write(iter.getKey() + ":" + iter.getValue());
		    	 nc_methods.newLine();
		    }
		    
		    nc_methods.write("Not covered: " + nmethods.size() + " out of " + StartMethods  + " [" + ((nmethods.size() * 100) / StartMethods) + "%]");
		    
		    for(Entry<String, Integer> iter : nedges.entrySet())
		    {
		    	 nc_edges.write(iter.getKey());
		    	 nc_edges.newLine();
		    }
		    
		    nc_edges.write("Not covered: " + nedges.size() + " out of " + StartEdges + " [" + ((nedges.size() * 100) / StartEdges) + "%]");

		    
		    for(Entry<String, Integer> iter : nedges_annotated.entrySet())
		    {
		    	 nc_edges_annotated.write(iter.getKey());
		    	 nc_edges_annotated.newLine();
		    }
		    
		    nc_edges_annotated.write("Not covered: " + nedges_annotated.size() + " out of " + StartEdgesAnnotated + " [" + ((nedges_annotated.size() * 100) / StartEdgesAnnotated) + "%]");
		    
		    // close the files
		    nc_methods.close();
		    nc_edges.close();
		    nc_edges_annotated.close();
	
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	
    }
    
    // --------------------------------------------------------------
    // if this method is called, it means that the corresponding call
    // site is executed. this should happen only for call sites that
    // are staticinvoke or specialinvoke. 

    // For virtual/interfaceinvoke, there should be another version of
    // the method that takes as a second parameter the receiver object
    // at the call site. The class of the receiver can be obtained at
    // run time using x.getClass().getName().

    public static void beforeCall(String call_site_id)
    { 
    	System.out.println("Call site: " + call_site_id);
    	Entry<String, Integer> Method = edges_annotated.floorEntry(call_site_id);
    	
    	nedges.remove((String)(call_site_id + "," + Method.getValue()));
    	nedges_annotated.remove((String)(call_site_id + "," + Method.getValue()));
    }
    
    public static void beforeCall(String call_site_id, Object Class)
    { 
    	System.out.println("Call site: " + call_site_id + "," + Class.getClass().getName());
    	Entry<String, Integer> Method = edges_annotated.floorEntry(call_site_id + "," + Class.getClass().getName());
    	
    	nedges.remove((String)(call_site_id + "," + Method.getValue()));
    	nedges_annotated.remove((String)(call_site_id + "," + Method.getValue() + "," + Class.getClass()));
    }


    // ---------------------------------------------------------
    // ok, this means that the excution just entered some method
    public static void methodEntry(int method_id)
    { 
    	System.out.println("Method: " + method_id);
		nmethods.remove(method_id);
    }

    // -------------------------------------------
    public static String percent(long x, long y) {
	double z = (100.0*x) / ((double)y);
	String res = new String (String.valueOf(Math.round(z)));
	return res + "%";
    }
}

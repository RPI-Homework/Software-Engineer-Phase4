public class ChaMain {

    // an object responsible for loading JIMPLE into memory
    public static final Loader loader = new Loader();

    // an object responsible for performing CHA
    public static final ChaAnalysis analysis = new ChaAnalysis();

    public static void main(String[] args) throws Exception {
	
	// Load all JIMPLE into memory. The command line provides some
	// necessary parameters for this. You don't need to understand
	// how this works
	loader.loadJimple(args);

	// name of the class that contains "main"
	analysis.setMainClass(args[1]);

	// name of the directory for the output files
	analysis.setOutputDir(args[2]);

	// run CHA
	analysis.analyze();

	// produce output files
	analysis.createOutput();
    }
}

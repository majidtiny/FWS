package fws_master;


import java.io.File;
import org.eclipse.swt.*;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.*;



public class FWSMaster {

	/**
	 * @param args
	 */
	private ParameterController parameter_controller;
	private StationController station_controller;
	@SuppressWarnings("unused")
	private ViewMain view;
	private Display display;
	private Shell shell;
	private MeasurementCollector collector;
	private String configDir;
	private String outDir;
	private int generatorTime;
	
	/*private void generateParameters() {
		Config_Parameter c = new Config_Parameter("Messintervall",this.parameter_controller);
		this.parameter_controller.addParameter(c);
		
		Input_Parameter i = new Input_Parameter("Temperatur",this.parameter_controller,Units.TEMPERATURE,Output_Formats.NK1,History_Functions.MAX);
		this.parameter_controller.addParameter(i);
		
		Station s = new Station("Dach",this.station_controller,"192.168.2.7:30000",10);
		this.station_controller.addStation(s);
		
		Station_Config_Binding cb = new Station_Config_Binding(s,c,0,1);
		Station_Input_Binding b = new Station_Input_Binding(s,i,1);
		
	}*/
	
	private FWSMaster(Shell shell, Display display,String configDir) {
		this.configDir = configDir;
		PersistencePreferences pref = new PersistencePreferences(configDir,"settings.xml");
		
		this.parameter_controller = pref.loadParameters();
		this.station_controller = pref.loadStations(this.parameter_controller);
		
		MasterContentHandler config = pref.loadMasterConfig();
		this.outDir = config.getPath();
		if (outDir.equals("")) {
			File outDirFile = new File(configDir,"output");
			if (!outDirFile.isDirectory()) {
				outDirFile.mkdir();
			}
			this.outDir = configDir+File.pathSeparator+"output";
		}
		
		this.generatorTime = config.getGeneratorTime();
		this.collector = new MeasurementCollector(this.station_controller,generatorTime,outDir);
		
		//this.generateParameters();
		view = new ViewMain(shell,display,this);
		this.shell = shell;
		this.display = display;
		this.collector.start();
	}
	
	public StationController getStationController() {
		return this.station_controller;
	}
	
	public static void main(String[] args) {
		Display display = new Display();
		Shell shell = new Shell(display);		
		
		
		String os = System.getProperty("os.name");
		String basePath = System.getProperty("user.home");
		String configDirPath = basePath+File.pathSeparator+".fwsmaster";
		if (os.equals("Mac OS X")) {
			configDirPath = basePath+"/Library/Application Support/FWSMaster";
			
		} 
		
		File configDir = new File(configDirPath);
		if(!configDir.isDirectory())
			configDir.mkdir();
		
		FWSMaster master = new FWSMaster(shell,display,configDirPath);
		
		shell.setSize(400,500);
		shell.open();
		shell.setText("FWS Master");
		
		
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep();
		}
		display.dispose();
		master.Shutdown();
		System.exit(0);
	}
	
	private void Shutdown() {
		PersistencePreferences pref = new PersistencePreferences(configDir,"settings.xml");
		pref.saveSettings(this.parameter_controller,this.station_controller,this.outDir,this.generatorTime);
		
	}

	public void ParameterClicked() {
		Shell param_shall = new Shell(display, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		Point pt = display.getCursorLocation();
		param_shall.setLocation (pt.x, pt.y);
		param_shall.setText ("Parameter Verwalten");
		param_shall.setSize (600, 400);
		
		@SuppressWarnings("unused")
		ViewParameters view_parameters = new ViewParameters(param_shall,this.parameter_controller);
		param_shall.open();
		
		return;
	}

	public void StationClicked() {
		Shell tmp_shell = new Shell(display, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		Point pt = display.getCursorLocation();
		tmp_shell.setLocation (pt.x, pt.y);
		tmp_shell.setText ("Stationen verwalten");
		tmp_shell.setSize (600, 400);
		
		@SuppressWarnings("unused")
		ViewStation view_stat = new ViewStation(tmp_shell,this.station_controller,this.parameter_controller);
		tmp_shell.open();
		
		return;
		
	}
	
	public void FolderClicked() {
		DirectoryDialog dialog = new DirectoryDialog(shell);
		String platform = SWT.getPlatform();
		dialog.setFilterPath (platform.equals("win32") || platform.equals("wpf") ? "c:\\" : "/");
		this.outDir =  dialog.open();
		this.collector.setOutDir(this.outDir);
	}
	
	public void StartClicked(boolean start) {
		this.station_controller.startStation(start);
	}

}
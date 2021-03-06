package fws_master;


import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Semaphore;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.eclipse.swt.*;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.*;



/**
 * This is the main class of this program. It creates all the windows, loads the configuration and starts the data processing threads.
 * The path of the configuration file is dependent of the operating system. 
 * The flow of the data is:
 * The User can add and edit parameters. These parameters represent measurements of the slaves. To get the values of the slaves, it's necessary
 * to add the slave to the master. For each slave you can setup which parameters should be polled of the slave. This process is called binding.
 * There are two kinds of bindings. SlaveConfigurationBindings and SlaveInputBindings. 
 * <ul>
 * <li>A ConfigurationBinding is transfered from the master to the slave</li>
 * <li>A InputBinding is transfered from the slave to the master</li>
 * </ul>
 * The configuration is transfered to the slave when the user requests it. During configuration phase the user sets a polling interval for 
 * each slave.
 * For each slave a own thread is started. This thread pulls the measurement values over the modbus protocol. Another Thread collects the
 * data from the slave threads. For each active InputBinding the collector thread creates an entry in a text file that represents the current
 * status of all sensors. 
 * The measurements are also added to a MeasurementHistory. This history is saved for each parameter on each slave. This History has no
 * real references to slaves or parameters. It saves all necessary data to plot diagrams. The history is separated in two parts.
 * <ol>
 * <li>Recent history: All values of the last hours. At least 24 hours are saved</li>
 * <li>Long term History: All values of each day are aggregated in one representing value (HistoryFunction determines how this value is computed).
 * This representing is saved in a history of the last 365 days</li>
 * </ol>
 * This history is serialized to the hard disk. 
 * For each binding the user can configure what diagrams should be generated. It's possible to generate a diagram of the last hours or of the 
 * long term history. It's also configurable how much data should be presented in the diagram. It's also possible to draw more one data series
 * in one diagram. 
 * 
 * This Application can only be closed over the "Exit" entry in the Menus. Otherwise it's minimized to the tray.
 * 
 *  The configuration is saved to a xml file (see PersistencePreferences, MasterContentHandler, ParameterContentHandler, SlaveContentHandler)
 * @author Johannes Kasberger
 *
 */
public class FWSMaster {
	private ParameterController parameter_controller;
	private SlaveController slave_controller;
	private ViewMain view;
	private Display display;
	private Shell shell;
	private MeasurementCollector collector;
	private String configDir;
	private String outDir;
	private int generatorTime;
	private static boolean closing = false;
	private static Logger log = Logger.getLogger("fws_master");
	private MenuItem trayHideItem, trayStartItem, trayExitItem;
	private boolean autoStart;
	private TrayItem trayItem;
	private Semaphore shutdownSem;
	private int plotWidth,plotHeight;
		
	/**
	 * Generates the configPath. Normally it's a folder .fwsmaster in the home directory.
	 * @param args
	 */
	public static void main(String[] args) {
		Display.setAppName("FWS Master");
		Display display = new Display();
		
		final Shell shell = new Shell(display, SWT.DIALOG_TRIM | SWT.MIN);
		// Set the Application Image
		Image appImg = new Image(display,FWSMaster.class.getResourceAsStream("/resources/logo.png") );
		shell.setImage(appImg);
		String configDirPath = generateConfigPath();
				
		//Create the Master
		FWSMaster master = new FWSMaster(shell,display,configDirPath);
		
		shell.setSize(400,500);
		shell.open();
		shell.setText("FWS Master");
		
		if (master.isAutoStart())
			shell.setVisible(false);
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep();
		}
		display.dispose();
		master.shutdown();
		System.exit(0);
	}

	public static String generateConfigPath() {
		//Generate configPath
		String os = System.getProperty("os.name");
		String basePath = System.getProperty("user.home");
		String configDirPath;
		
		if (os.equals("Mac OS X")) {
			configDirPath = basePath+"/Library/Application Support/FWSMaster";
		} else {
			configDirPath= basePath+File.separator+".fwsmaster";
		}
		
		//Create configDir if not existent
		File configDir = new File(configDirPath);
		if(!configDir.isDirectory())
			configDir.mkdir();
		return configDirPath;
	}
	
	public FWSMaster() {
		this.configDir = FWSMaster.generateConfigPath();
		lowLevelInit();
		log.info("Loading as Daemon, configDir: "+this.configDir);
		this.loadConfig();
		log.info("Config Loaded");
	}
	
	public void start() {
		this.collector.start();
		this.slave_controller.startSlaves(true);
		log.info("daemon started");
	}
	
	public void stop() {
		this.slave_controller.startSlaves(false);
		this.collector.stopThread();
		try {
			this.collector.join(100);
		} catch (InterruptedException e) {
			log.severe(e.getMessage());
		}
		//don't call this.shutdown() because config changes are not saved in daemon mode
		log.info("daemon stopped");
	}
	
	/**
	 * Constructor creates a MainView and creates the Tray Icon
	 * @param shell The SWT Shell
	 * @param display the SWT Display
	 * @param configDir the os dependent config path
	 */
	private FWSMaster(final Shell shell, Display display,String configDir) {
		this.configDir = configDir;
		
		lowLevelInit();
		
		this.shell = shell;
		this.display = display;
		
		//only enable disposing when exit in menu was called. Otherwise hide the shell
		shell.addListener(SWT.Close, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (!closing) {
					event.doit = false;
					trayHideItem.setText("Show");
					shell.setVisible(false);
				}
			}
		});
		
		this.createTray();
		//Load the preferences 
		
		
		this.loadConfig();
		
		view = new ViewMain(shell,display,this);
		
		this.collector.start();
		if (this.autoStart) {
			this.StartClicked(true);
			this.shell.setVisible(true);
			this.view.toogleStartButton();
			HideShow();
		}
		else {
			this.shell.setVisible(false);
			HideShow();
		}
	}

	private void lowLevelInit() {
		//Generate the Log File
		try {
			//max. 2 mb log file 
			FileHandler fh = new FileHandler(this.configDir+"/fws_master%g.log", 2000000,3,true);
			log.addHandler(fh);
			log.setLevel(Level.INFO);
			SimpleFormatter formatter = new SimpleFormatter();
			fh.setFormatter(formatter);
		} catch (Exception ex) {
			System.out.println("Error during creating log Handler: "+ex.getMessage());
		}
		
		shutdownSem = new Semaphore(1);
	}
	
	/**
	 * Creates a Tray Icon with some useful menu items
	 */
	private void createTray() {
		Tray tray = display.getSystemTray();
		
		if(tray != null) {
			trayItem = new TrayItem(tray, SWT.NONE);
			trayItem.setToolTipText("Stop");
			TrayItemListener l = new TrayItemListener();
			Image trayImg = new Image(display,FWSMaster.class.getResourceAsStream("/resources/tray.png") );
			trayItem.setImage(trayImg);
			
			final Menu menu = new Menu(shell, SWT.POP_UP);
			trayHideItem = new MenuItem(menu, SWT.PUSH);
			trayHideItem.setText("Hide");
			trayHideItem.addSelectionListener(l);
			
			trayStartItem = new MenuItem(menu, SWT.PUSH);
			trayStartItem.setText("Start");
			trayStartItem.addSelectionListener(l);
			
			trayExitItem = new MenuItem(menu, SWT.PUSH);
			trayExitItem.setText("Exit");
			trayExitItem.addSelectionListener(l);
			
			// Show/Hide MainView on DoubleClick on TrayIcon
			trayItem.addSelectionListener(new SelectionListener() {

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
					HideShow();
				}

				@Override
				public void widgetSelected(SelectionEvent e) {
					
				}
				
			});
			
			// Show menu on right mouse button
			trayItem.addListener (SWT.MenuDetect, new Listener () {
				public void handleEvent (Event event) {
					menu.setVisible(true);
				}
			});
		}
	}

	/**
	 * Loads the Configuration of the settings.xml
	 */
	private void loadConfig() {
		PersistencePreferences pref = new PersistencePreferences(configDir,"settings.xml");
		this.parameter_controller = pref.loadParameters();
		this.slave_controller = pref.loadSlaves(this.parameter_controller);
		MasterContentHandler config = pref.loadMasterConfig();
		
		this.outDir = config.getPath();
		//if there is no outDir given create on in the configDir
		try {
			File testOutDir = new File(this.outDir);
			if (!testOutDir.isDirectory())
				throw new Exception("no dir");
		} catch (Exception ex) {
			File outDirFile = new File(configDir,"output");
			if (!outDirFile.isDirectory()) {
				outDirFile.mkdir();
			}
			this.outDir = configDir+File.separatorChar+"output";
		}
				
		
		log.config("Output Directory: "+this.outDir);
		
		this.generatorTime = config.getGeneratorTime();
		this.autoStart = config.isAutoStart();
		this.setPlotWidth(config.getPlotWidth());
		this.setPlotHeight(config.getPlotHeight());
		this.collector = new MeasurementCollector(this,this.slave_controller,generatorTime,outDir,configDir);
	}
	
	/**
	 * Get the SlaveController
	 * @return the controller
	 */
	public SlaveController getSlaveController() {
		return this.slave_controller;
	}
	
	
	
	/**
	 * Save the current settings in the xml config file
	 */
	private void shutdown() {
		this.blockShutdown();
		PersistencePreferences pref = new PersistencePreferences(configDir,"settings.xml");
		pref.saveSettings(this.parameter_controller,this.slave_controller,this.outDir,this.generatorTime,this.autoStart, this.getPlotWidth(),this.getPlotHeight());
	}

	/**
	 * Open the parameter window
	 */
	public void ParameterClicked() {
		Shell param_shall = new Shell(display, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		Point pt = display.getCursorLocation();
		param_shall.setLocation (pt.x, pt.y);
		param_shall.setText ("Configure Parameters");
		param_shall.setSize (600, 400);
		
		@SuppressWarnings("unused")
		ViewParameters view_parameters = new ViewParameters(param_shall,this.parameter_controller);
		param_shall.open();
		
		return;
	}

	/**
	 * Open the slave window
	 */
	public void SlaveClicked() {
		Shell tmp_shell = new Shell(display, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		Point pt = display.getCursorLocation();
		tmp_shell.setLocation (pt.x, pt.y);
		tmp_shell.setText ("Configure Slaves");
		tmp_shell.setSize (600, 700);
		
		@SuppressWarnings("unused")
		ViewSlave view_stat = new ViewSlave(tmp_shell,this.slave_controller,this.parameter_controller,this);
		tmp_shell.open();
		
		return;
		
	}
	
	/**
	 * Open the outDir config window
	 */
	public void FolderClicked() {
		DirectoryDialog dialog = new DirectoryDialog(shell);
		
		dialog.setFilterPath(this.outDir);
		
		String newOutDir = dialog.open();
		if (newOutDir == null || newOutDir.equals(""))
			return;
		this.outDir = newOutDir;
		this.collector.setOutDir(this.outDir);
	}
	
	/**
	 * Start or Stopp all slaves
	 * @param start if true start the slaves, if false pause them
	 */
	public void StartClicked(boolean start) {
		this.slave_controller.startSlaves(start);
		this.view.enableMenu(!start);
		
		if (start) {
			trayStartItem.setText("Stop");
			trayExitItem.setEnabled(false);
			trayItem.setToolTipText("Running");
		} else {
			trayStartItem.setText("Start");
			trayExitItem.setEnabled(true);
			trayItem.setToolTipText("Stop");
		}
	}

	/**
	 * Show the add slave view
	 */
	public void viewAddSlaveClicked() {
		Shell tmp_shell = new Shell(display, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		Point pt = display.getCursorLocation();
		tmp_shell.setLocation (pt.x, pt.y);
		tmp_shell.setText ("Add new Slave");
		tmp_shell.setSize (300, 200);
		
		new ViewNew(tmp_shell,this);
		tmp_shell.open();
	}
	/**
	 * Called when User selects add slave from the fileMenu
	 */
	public boolean addSlaveClicked(String name, String ip) {
		try {
			Slave newSlave  = new Slave(name, this.slave_controller, ip, 60);
			this.slave_controller.addSlave(newSlave);
			this.reloadSlaveView();
		} catch (Exception ex) {
			return false;
		}
		return true;
	}
	
	/**
	 * Called when User selects save Config from the fileMenu
	 */
	public void saveConfigClicked() {
		PersistencePreferences pref = new PersistencePreferences(configDir,"settings.xml");
		pref.saveSettings(this.parameter_controller,this.slave_controller,this.outDir,this.generatorTime,this.autoStart,this.getPlotWidth(),this.getPlotHeight());
	}
	
	/**
	 * Called when User selects reload Config from the fileMenu
	 */
	public void reloadConfigClicked() {
		this.loadConfig();
		this.reloadSlaveView();
	}
	
	/**
	 * Called when the user selects the settings entry from the configMenu. Shows the Settings view.
	 */
	public void settingsClicked() {
		Shell tmp_shell = new Shell(display, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		Point pt = display.getCursorLocation();
		tmp_shell.setLocation (pt.x, pt.y);
		tmp_shell.setText ("Settings");
		tmp_shell.setSize (200, 300);
		
		new ViewSettings(tmp_shell,this);
		tmp_shell.open();
	}

	/**
	 * Enables the disposing of the display and disposes it. Only allow closing when the Slaves are stopped.
	 */
	public void exitClicked() {
		if (!this.slave_controller.isRunning()) {
			closing = true;
			display.dispose();
		}
	}
	
	/**
	 * Toggle the Main View
	 */
	private void HideShow() {
		if (shell.isVisible()) {
			shell.setVisible(false);
			trayHideItem.setText("Show");
		}
		else {
			shell.setVisible(true);
			shell.forceActive();
			shell.forceFocus();
			trayHideItem.setText("Hide");
		}
	}

	/**
	 * Creates a View for the currently saved data
	 */
	public void viewDataClicked() {
		Shell tmp_shell = new Shell(this.display, SWT.RESIZE | SWT.CLOSE | SWT.TITLE);
		Point pt = display.getCursorLocation();
		tmp_shell.setLocation (pt.x, pt.y);
		tmp_shell.setText ("Collected Data");
		tmp_shell.setSize (700, 800);
		
		new ViewData(this,tmp_shell);
		tmp_shell.open();
	}
	
	public void aboutClicked() {
		Shell tmp_shell = new Shell(this.display, SWT.RESIZE | SWT.CLOSE | SWT.TITLE);
		Point pt = display.getCursorLocation();
		tmp_shell.setLocation (pt.x, pt.y);
		tmp_shell.setText ("About");
		tmp_shell.setSize (400, 300);
		
		new ViewAbout(tmp_shell);
		tmp_shell.open();
	}

	public void homepageClicked() {
		try {
			java.awt.Desktop.getDesktop().browse(new URI("http://github.com/schugabe/fws"));
		} catch (IOException e) {
			log.severe("Error opening homepage: "+e.getMessage());
			e.printStackTrace();
		} catch (URISyntaxException e) {
			log.severe("Error opening homepage: "+e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Listener for Tray Events
	 * @author Johannes Kasberger
	 *
	 */
	class TrayItemListener extends SelectionAdapter {
		public void widgetSelected(SelectionEvent event) {
			if (((MenuItem) event.widget)==trayHideItem) {
				HideShow();
				
			}
			else if (((MenuItem) event.widget)==trayStartItem) {
				if (slave_controller.isRunning()) {
					StartClicked(false);
				}
				else {
					StartClicked(true);
				}
				view.toogleStartButton();
			}
			else if (((MenuItem) event.widget)==trayExitItem) {
				exitClicked();
			}
		}
	}

	/**
	 * Get the Generator Time
	 * @return the time interval in seconds in that the output is generated
	 */
	public int getGeneratorTime() {
		return generatorTime;
	}

	/**
	 * The Generatortime is the time interval after that the outputs are generated
	 * @param generatorTime
	 */
	public void setGeneratorTime(int generatorTime) {
		this.generatorTime = generatorTime;
	}

	/**
	 * @param autoStart the autoStart to set
	 */
	public void setAutoStart(boolean autoStart) {
		this.autoStart = autoStart;
	}

	/**
	 * @return the autoStart
	 */
	public boolean isAutoStart() {
		return autoStart;
	}
	
	
	/**
	 * Get the current History Controller
	 * @return the controller
	 */
	public MeasurementHistoryController getHistoryController() {
		return this.collector.getMeasurementHistoryController();
	}
	
	public synchronized boolean blockShutdown() {
		try {
			this.shutdownSem.acquire();
		} catch (InterruptedException e) {
			return false;
		}
		return true;
	}
	
	public synchronized void releaseShutdown() {
		this.shutdownSem.release();
	}

	public void reloadSlaveView() {
		
	}

	/**
	 * @param plotWidth the plotWidth to set
	 */
	public void setPlotWidth(int plotWidth) {
		this.plotWidth = plotWidth;
	}

	/**
	 * @return the plotWidth
	 */
	public int getPlotWidth() {
		return plotWidth;
	}

	/**
	 * @param plotHeight the plotHeight to set
	 */
	public void setPlotHeight(int plotHeight) {
		this.plotHeight = plotHeight;
	}

	/**
	 * @return the plotHeight
	 */
	public int getPlotHeight() {
		return plotHeight;
	}

	
}

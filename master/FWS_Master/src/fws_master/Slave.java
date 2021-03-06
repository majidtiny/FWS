package fws_master;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Logger;

import org.eclipse.swt.widgets.Label;

/**
 * A Slave represents a slave. It has a list of Parameters that can be configuration or input parameters. When the thread is started
 * the input parameters are pulled from the slave and saved in a measurements list.
 * The configuration is sent when requested.
 * @author Johannes Kasberger
 *
 */
public class Slave extends Thread{
	private Vector<Binding> parameters;
	private Vector<Measurement> measurements;
	private String ipAddress;
	private int polling_interval;
	private String name;
	private Label statusLabel;
	private SlaveController controller;
	private volatile boolean suspended;
	private ModBusWrapper wrapper;
	private static Logger log = Logger.getLogger("fws_master.slave");
	public static String defaultIP = "10.0.0.29:502";
	/**
	 * The Slave must belong to a controller and have a unique name.
	 * @throws Exception 
	 */
	public Slave(String name,SlaveController controller) throws Exception {
		this.setSlaveName(name);
		this.controller = controller;
		this.polling_interval = 60;
		this.ipAddress = defaultIP;
		this.statusLabel = null;
		this.init();
	}
	
	/**
	 * Create a Slave with ip and polling_intervall
	 * @param name
	 * @param controller
	 * @param ip
	 * @param polling_intervall
	 * @throws Exception 
	 */
	public Slave(String name,SlaveController controller,String ip,int polling_intervall) throws Exception {
		this.setSlaveName(name);
		this.controller = controller;
		this.polling_interval = polling_intervall;
		if (checkIP(ip))
			this.ipAddress = ip;
		else
			this.ipAddress = defaultIP;
		this.statusLabel = null;
		this.init();
	}
	
	/**
	 * create the intern lists
	 */
	private void init() {
		this.parameters = new Vector<Binding>();
		this.measurements = new Vector<Measurement>();
		this.suspended = true;
		this.setName(this.name);
		
	}
	
	/**
	 * Resume the data collection process
	 */
	public void resumeSlave() {
		this.suspended = false;
		synchronized(this) {notify();}
	}
	
	/**
	 * Pause the data collection process
	 */
	public void pauseSlave() {
		this.suspended = true;
	}
	
	/**
	 * Setting the status Label must happen over this method. Otherwise it's executed in the wrong context.
	 * @param msg
	 */
	private void setLabel(final String msg) {
		if (this.statusLabel == null)
			return;
		
		this.statusLabel.getDisplay().asyncExec(new Runnable() {
			public void run()
			{
				Calendar cal = Calendar.getInstance();
			    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
			    String date =  sdf.format(cal.getTime());

				statusLabel.setText(""+msg+" at "+ date);
			}
		});
	}
	
	/**
	 * Start the data collection thread
	 */
	public void run() {
		this.suspended = false;
		wrapper = new ModBusWrapper(this.ipAddress);
		while (true) {
			try {
				if (this.suspended) {
					this.setLabel("Pause");
					synchronized(this) {
						while(this.suspended)
							wait();
					}
					setLabel("Running");
				}
				log.fine(this.getSlaveName()+" start pulling values");
				this.getMeasurements();
				Thread.sleep(this.polling_interval*1000);
				
			} catch (InterruptedException e) {
				
			}
		}

	}
	
	/**
	 * For each active SlaveInputBinding a value is transfered from the slave to the master.
	 */
	private void getMeasurements() {
		String newLabel = "Online";
		
		for(Binding b:this.parameters) {
			if (b instanceof SlaveInputBinding) {
				if (!((SlaveInputBinding) b).isActive())
					continue;
				
				int result;
				try {
					result = wrapper.sendReadRequest(b.getAddress());
				} catch (Exception e) {
					newLabel = "Read failed";
					continue;
				}
				
				Measurement m = new Measurement(this,(InputParameter) b.getParameter(),result);
				synchronized(this.measurements) {
					this.measurements.add(m);
					log.fine("Measurement saved: "+this.name+" "+m.getParameter().getName()+" "+m.getConvValue());
				}
			}
		}
		this.setLabel(newLabel);
	}
	
	/**
	 * Return the corresponding Binding for a Parameter
	 * @param p the Parameter that is bound 
	 * @return the binding of parameter p, null if parameter p is not bound to this slave
	 */
	public Binding getBinding(Parameter p) {
		for (Binding b:this.getBindings()) {
			if(b.getParameter() == p) 
				return b;
		}
		return null;
	}
	
	/**
	 * Get all Bindings that are bound to this Slave
	 * @return List of all Bindings
	 */
	public Vector<Binding> getBindings() {
		return this.parameters;
	}
	
	/**
	 * If the ip address has changed it's saved after the transfer to the slave. The ip address is converted to two 16 bit values
	 * and sent to the old ip address. Afterwards the newIP is saved. 
	 * @param newIP The ip address of the slave after changing it. Must be unique.
	 * @return false if changing of ip failed
	 */
	public boolean changeIPAddress(String newIP) {
		if (!checkIP(newIP))
			return false;
		
		ModBusWrapper config_wrapper = new ModBusWrapper(this.ipAddress);
		
		//Change ip address
		if(newIP != null && !this.ipAddress.equals(newIP)) {
			
			for (Slave s:this.controller.getSlaves()) {
				if (s.getIpAddress().equals(newIP)) {
					return false;
				}
			}
			int []int_ip = this.convertIP(newIP);

			if (!config_wrapper.sendWriteRequest(0, int_ip[0]))
				return false;
			if (!config_wrapper.sendWriteRequest(1, int_ip[1]))
				return false;
			
			this.ipAddress = newIP;
		}
		
		return true;
	}
	
	/**
	 * Converts a string of an ip address to two int values
	 * @param ip
	 * @return array of two integer values representing the ip address. the ip address 127.0.0.1 will be converted to 127<<8|0 and 0<<8|1
	 */
	private int[] convertIP(String ip) {
		int [] conv = new int[2];
		int idx = ip.indexOf(':');
	    
	    if(idx > 0) {
	      ip = ip.substring(0,idx);
	    }
	    
	    int tmp = 0;
	    String sub;
	    StringTokenizer st = new StringTokenizer(ip,".");
	    while (st.hasMoreTokens()) {
	         sub = st.nextToken();
	         if (tmp == 0) {
	        	 conv[0] = Integer.parseInt(sub) << 8;
	         }
	         else if (tmp == 1) {
	        	 conv[0] |= Integer.parseInt(sub);
	        	 
	         }
	         else if (tmp == 2) {
	        	 conv[1] = Integer.parseInt(sub) << 8;
	         }
	         else if (tmp == 3) {
	        	 conv[1] |= Integer.parseInt(sub);
	         }
	         else
	        	 break;
	         tmp++;
	     }

		
		return conv;
	}
	
	/**
	 * Transfer all changed configuration parameters to the slave. 
	 * @return false upload was not successful
	 */
	public boolean uploadParamsConfig() {
		ModBusWrapper config_wrapper = new ModBusWrapper(this.ipAddress);
		// Send configuration to slave
		for(Binding b:this.getBindings()) {
			if (b instanceof SlaveConfigBinding) {
				if (!b.isActive())
					continue;
				SlaveConfigBinding cb = (SlaveConfigBinding)b;
				if (cb.isTransfered())
					continue;
				if (!config_wrapper.sendWriteRequest(cb.getAddress(),cb.getValue()))
					log.warning("Transfering configuration value was not successful: "+this.name+":"+this.ipAddress+" Parameter"+cb.getParameter().getName()+" at address "+cb.getAddress()+" with value "+cb.getValue());
				else
					cb.setTransfered(true);
			}
		}
		return true;
	}
	
	/**
	 * Releases all bindings
	 */
	public void deleteSlave(){
		for(Binding binding:this.parameters) {
			binding.releaseParameter();
		}
	}
	
	/**
	 * Add a Binding to this Slave
	 * @param binding
	 */
	public void addBinding(Binding binding) {
		this.parameters.add(binding);
	}
	
	/**
	 * Remove a Binding from this Slave
	 * @param binding binding to be removed
	 * @return true if binding was found an removed
	 */
	public boolean removeBinding(Binding binding) {
		if (this.parameters.remove(binding)) {
			binding.releaseParameter();
			return true;
		}
		return false;
	}

	/**
	 * Change the Label Text of this slave
	 * @param l text for the label
	 */
	public void setStatusLabel(Label l) {
		this.statusLabel = l;
	}

	/**
	 * @return the ip_address
	 */
	public String getIpAddress() {
		return ipAddress;
	}

	/**
	 * PollingIntevall is the duration in seconds that is waited before the slaves pulls the measurements of a slave.
	 * @param polling_interval the interval
	 */
	public void setPollingInterval(int polling_interval) {
		this.polling_interval = polling_interval;
	}

	/**
	 * @return the PollingInterval
	 */
	public int getPollingInterval() {
		return polling_interval;
	}

	/**
	 * Set the Name of the Slave
	 * @param name Name of the Slave
	 * @throws Exception 
	 */
	public void setSlaveName(String name) throws Exception {
		String tmp = name.trim();
		if (!tmp.matches("(\\w| )+"))
			throw new Exception("Invalid Slave name");
		this.name = tmp;
	}

	/**
	 * @return the name of the Slave
	 */
	public String getSlaveName() {
		return name;
	}
	
	/**
	 * Get the count of the active input parameters of this slave
	 * @return count of active input parameters
	 */
	public int getInputParamsCount() {
		int tmp = 0;
		for(Binding b:this.parameters) {
			if (b instanceof SlaveInputBinding && b.isActive())
				tmp++;
		}
		return tmp;
	}
	
	/**
	 * Get a List of the received measurements since the last call of this function. After getting the measurements this list is cleared.
	 * @return list of measurements
	 */
	public Vector<Measurement> getLastMeasurements() {
		Vector<Measurement> tmp;
		
		synchronized(this.measurements) {
			tmp = new Vector<Measurement>(measurements);
			this.measurements.clear();
		}
		return tmp;
	}
	
	private boolean checkIP(String ip) {
		return ip.matches("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\:[0-9]{1,5}");
	}
}

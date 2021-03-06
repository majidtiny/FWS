package fws_master;

/**
 * A input parameter is transfered from the slave to the master an represents a type of collected data. 
 * This data has a unit (e.g. speed) and a OutputFormat. This Format configures how the short value from the slave is converted to a double value.
 * The HistoryFunction calculates a summary of the last day. This value is saved in a History. 
 * @author Johannes Kasberger
 * @see Parameter
 * @see SlaveInputBinding
 * @see Units
 * @see HistoryFunctions
 * @see OutputFormats
 */
public class InputParameter extends Parameter{
	private Units unit;
	private OutputFormats format;
	private HistoryFunctions history_function;
	private float filter;
	
	/**
	 * A InputBinding must have a Name and must have a controller
	 * @param name
	 * @param controller
	 * @throws Exception 
	 */
	public InputParameter(String name,ParameterController controller) throws Exception {
		super(name,controller);
		setFilter(1.0f);
	}
	
	/**
	 * Generates a InputBinding with Values
	 * @param name Name of the Binding e.g. Temperature
	 * @param controller Controller of this Binding
	 * @param unit Unit of this Binding
	 * @param format Format of this Binding
	 * @param history_function History Function of this Binding
	 * @throws Exception 
	 */
	public InputParameter(String name,ParameterController controller, Units unit,OutputFormats format, HistoryFunctions history_function, float filter) throws Exception {
		super(name,controller);
		this.format = format;
		this.unit = unit;
		this.history_function = history_function;
		setFilter(filter);
	}

	/**
	 * @param filter the filter to set
	 */
	public void setFilter(float filter) {
		if (filter > 1.0f)
			filter = 1.0f;
		if (filter <= 0.0f)
			filter = 0.0001f;
		this.filter = filter;
	}

	/**
	 * @return the filter
	 */
	public float getFilter() {
		return filter;
	}
	
	/**
	 * @param unit the unit to set
	 */
	public void setUnit(Units unit) {
		this.unit = unit;
	}

	/**
	 * @return the unit
	 */
	public Units getUnit() {
		return unit;
	}

	/**
	 * @param format the format to set
	 */
	public void setFormat(OutputFormats format) {
		this.format = format;
	}

	/**
	 * @return the format
	 */
	public OutputFormats getFormat() {
		return format;
	}

	/**
	 * @param history_function the history_function to set
	 */
	public void setHistory_function(HistoryFunctions history_function) {
		this.history_function = history_function;
	}

	/**
	 * @return the history_function
	 */
	public HistoryFunctions getHistory_function() {
		return history_function;
	}

}

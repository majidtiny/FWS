package fws_master;

/**
 * Possible conversation options of the input values 
 * @author Johannes Kasberger
 *
 */
public enum OutputFormats {
	UNKNOWN,NK0,NK1,NK2,NK3,NK4,NK5;
	/**
	 * Get a String for the corresponding formating options
	 * @param format
	 * @return a String
	 */
	static public String getString(OutputFormats format) {
		switch(format) {
		case NK0: return "65536";
		case NK1: return "6553.6";
		case NK2: return "655.36";
		case NK3: return "65.536";
		case NK4: return "6.5536";
		case NK5: return "0.65536";
		default: return "";
		}
	}

	/**
	 * Convert the strings back to the OutputFormats value
	 * @param format
	 * @return OutputFormats
	 */
	static public OutputFormats getFormat(String format) {
		if (format.equals("65536"))
			return NK0;
		else if (format.equals("6553.6"))
			return NK1;
		else if (format.equals("655.36"))
			return NK2;
		else if (format.equals("65.536"))
			return NK3;
		else if (format.equals("6.5536"))
			return NK4;
		else if (format.equals("0.65536"))
			return NK5;
		return UNKNOWN;
	}
}

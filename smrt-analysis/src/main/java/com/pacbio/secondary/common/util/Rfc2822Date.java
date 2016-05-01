package com.pacbio.secondary.common.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.xml.bind.annotation.adapters.XmlAdapter;

/**
 * An RFC-2822 date.
 */
public class Rfc2822Date extends XmlAdapter<String,Date> {
	
	/** Date format for RFC-2822. */
    public final static DateFormat DATE_FORMAT = 
    	new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);

    /** Older RFC-822 date format with two-digit year. */
    public final static DateFormat DATE_FORMAT_822 = 
    	new SimpleDateFormat("EEE, dd MMM yy HH:mm:ss Z", Locale.US);

    /**
     * Format a date as RFC-2822 or 822
     * @param date
     * @return date of the form ''
     */
    public static String format(Date date) {
		return DATE_FORMAT.format(date);    	
    }
    
    /**
     * Parse an RFC-822 date.
     * @param s string to parse
     * @return parsed date/time
     * @throws ParseException
     */
    public static Date parse(String s) throws ParseException {
    	if (s == null || s.trim().length() == 0 || "null".equalsIgnoreCase(s))
    		return null;
		Date date = null;
		try {
			date = DATE_FORMAT.parse(s);
		} catch (ParseException e) {
			date = DATE_FORMAT_822.parse(s);
		}
		return date;
    }
    
    public Date unmarshal(String s) throws Exception {
		return DATE_FORMAT.parse(s);
	}

	public String marshal(Date date) throws Exception {
		return DATE_FORMAT.format(date);
	} 			
}

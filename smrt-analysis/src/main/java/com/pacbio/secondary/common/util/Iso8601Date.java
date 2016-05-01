package com.pacbio.secondary.common.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import javax.xml.bind.annotation.adapters.XmlAdapter;

/**
 * An ISO-8601 date with time zone.
 */
public class Iso8601Date extends XmlAdapter<String,Date> {
	/** Standard date format for ISO-8601. */
    public final static DateFormat DATE_FORMAT = 
    	new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    /* ISO-8601 date format with no time zone. */
    private final static DateFormat NO_TIME_ZONE = 
    	new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    /* ISO-8601 date format with no time. */
    private final static DateFormat NO_TIME = 
    	new SimpleDateFormat("yyyy-MM-dd");

    /* A slight variation that sometimes comes back from MySQL */
    private final static DateFormat MYSQL_INTERNAL = 
    	new SimpleDateFormat("yyyy-MM-dd HH:mm:ss'.0'");
    
    private final static Logger LOGGER = Logger.getLogger(Iso8601Date.class.getName());

    /**
     * Format a date as ISO-8601. 
     * Local time with UTC offset.
     * Most callers should user this
     * @param date
     * @return date of the form '2010-01-01T01:02:03-0700'
     */
    public static String format(Date date) {
		return DATE_FORMAT.format(date);
    }
    
    /**
     * Format a date as ISO-8601.
     * Takes a date, applies no timezone information, and returns it the way C# expects to see UTC
     * @param date
     * @return date of the form '2010-01-01T01:02:03Z'
     */
    public static String formatForCSharp(Date date) {
    	//bug 15914 - skip time zone offset, and assume that date is already in UTC time.
    	//hack for last-timestamp rest api
		return NO_TIME_ZONE.format(date) + "Z";    	
    }
    
    /**
     * Parse an ISO-8601 date.
     * @param s string to parse
     * @return parsed date/time
     * @throws ParseException
     */
    public static Date parse(String s) throws ParseException {
    	if (s == null || s.trim().length() == 0 || "null".equalsIgnoreCase(s))
    		return null;
    	
    	for (DateFormat df : new DateFormat[] {
    			DATE_FORMAT, NO_TIME_ZONE, MYSQL_INTERNAL, NO_TIME
    		}) {
    		try {
				LOGGER.finer("Parse date with format " + df);
    			return df.parse(s);
			} catch (ParseException e) {
				LOGGER.finer("Failed to parse date " + s + " with format " + df);
    		}
    	}
    	LOGGER.warning("Failed to parse date: " + s);
		return null;
    }
    
    @Override
	public String marshal(Date date) {
		return format(date);
	}

	@Override
	public Date unmarshal(String s) throws ParseException {
		return parse(s);
	}
}

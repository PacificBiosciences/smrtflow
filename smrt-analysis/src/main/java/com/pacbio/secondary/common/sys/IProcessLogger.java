/**
 * 
 */
package com.pacbio.secondary.common.sys;

/**
 * This interface can be used to wrap your logger so that it can be passed to
 * {@link SysCommandExecutor}. The output from the external process will then be
 * logged to your logger.
 * 
 * @author jmiller
 */
public interface IProcessLogger {

	/**
	 * Log a message
	 * @param line
	 */
	void log(String line);

	/**
	 * Log a message and exception
	 * @param line
	 * @param t
	 */
	void log(String line, Throwable t);

}

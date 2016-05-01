/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.pacbio.secondary.common.core.AnalysisConfig;
import com.pacbio.secondary.common.core.AnalysisContext;

/**
 * Wrapper for information about {@link ReferenceUploader} that can be written
 * to the log at execution time.
 * 
 * @author jmiller
 */
public class Help {

	
	private final static Logger LOGGER = Logger.getLogger(Help.class.getName());

	private static Help instance;

	private AnalysisConfig config;

	private Help() {
	}

	public static Help instance() {
		if (instance == null)
			instance = new Help();
		return instance;
	}

	@Override
	public String toString() {
		final String S = System.getProperty("line.separator");
		
		StringBuffer sb = new StringBuffer();
		
		sb.append(S ).append("********** " + ReferenceUploader.class.getSimpleName() );
		sb.append(S ).append("********** Version: " + getVersion());

		return sb.toString();
	}

	/**
	 * Get the version of the uploader. Since ReferenceUploader is not defined
	 * as a standalone component in the system, we use the version obtained for
	 * SMRTPipe.
	 * 
	 * @return
	 */
	public String getVersion() {
		if (getConfig() == null)
			return "1.1";
		return getConfig().getComponents().getComponent("SMRTpipe").getVersion();
	}

	/**
	 * Get the config object, if possible
	 * 
	 * @return null if config cannot be found
	 */
	private AnalysisConfig getConfig() {
		if (config != null)
			return config;

		try {
			config = AnalysisContext.getInstance().getConfig();
		} catch (Exception e) {
			LOGGER.log(Level.WARNING,
					"Can't get the version out of config.xml, so using default.");
		}
		return config;
	}

}

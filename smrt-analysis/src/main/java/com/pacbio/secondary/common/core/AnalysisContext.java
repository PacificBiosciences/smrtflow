package com.pacbio.secondary.common.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Static convenience methods to get SMRT analysis system paths. Paths are
 * automatically reloaded to avoid stale values. They can be manually reloaded
 * using {@link #reload()}.
 */
public class AnalysisContext extends AbstractAnalysisContext {
	
	
	private static AnalysisContext instance;

	AnalysisConfig analysisConfig;

	/**
	 * Singleton.
	 */
	synchronized public static AnalysisContext getInstance() {
		if (instance == null)
			instance = new AnalysisContext();
		return instance;
	}

	/**
	 * Initialize the analysis context overriding the root. Useful for unit
	 * testing.
	 * 
	 * @param root
	 *            use this path instead of the default; if null, revert to the
	 *            default
	 */
	public static void initialize(File root) throws FileNotFoundException {
		AnalysisConfig.setAnalysisRoot(root);
		instance = null;
	}

	/**
	 * The root directory of the analysis system. Create it if it doesn't exist.
	 * @throws IOException 
	 */
	public File getAnalysisRoot() {
		return AnalysisConfig.getAnalysisRoot();
	}


	/**
	 * Forcibly reload paths from the config file.
	 */
	synchronized public void reload() {
		LOGGER.info("Reload analysis config");
		analysisConfig = null;
	}

	/**
	 * Get the paths from cache or reload if older than updateFrequency.
	 */
	synchronized public AnalysisConfig getConfig() throws IOException {
		if (analysisConfig == null) {
			LOGGER.fine("Load config ");
			try {
				analysisConfig = AnalysisConfig.open();
			} catch (IOException e) {
				String msg = "Analysis config file could not be opened";
				LOGGER.log(Level.SEVERE, msg);
				throw new IOException(msg, e);
			}
		}
		return analysisConfig;
	}

}

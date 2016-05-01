/**
 * 
 */
package com.pacbio.secondary.common.core;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * @author jmiller
 * 
 */
public abstract class AbstractAnalysisContext {

	protected static final Logger LOGGER = Logger
			.getLogger(AbstractAnalysisContext.class.getName());

	public abstract File getAnalysisRoot();

	/**
	 * The absolute path of the common jobs directory.
	 */
	public File getJobsRoot() {
		return getAbsolutePath(AbstractAnalysisConfig.JOB_DIR, new File(
				"common", "jobs"));
	}

	
	public File getTempDir() {
		return getAbsolutePath(AbstractAnalysisConfig.TEMP_DIR,
				new File("../..", "tmpdir"));
	}

	/**
	 * The absolute path of the common log directory. SMRT Analysis applications
	 * will log to subdirectories using {@link #getLogHandler}
	 */
	public File getLogRoot() {
		return getAbsolutePath(AbstractAnalysisConfig.LOG_DIR, new File(
				"common", "log"));
	}

	/**
	 * Initialize a root logger for the application using a standard log
	 * handler, created by {@link #getLogHandler}. This will create a log all
	 * messages from all libraries with an {@link Level} of info. For more
	 * control, use {@link #initLogger(String, String, Level)}
	 * 
	 * @param dirName
	 * @return the logger
	 */
	public Logger initLogger(String dirName) {
		return initLogger("", dirName, Level.INFO);
	}

	/**
	 * Initialize a root logger for the application using a standard log
	 * handler, created by {@link #getLogHandler}.
	 * 
	 * @param root
	 *            the log root. For example "" will log messages from all
	 *            libraries. "com.pacbio.secondary" will log messages from the
	 *            Pacbio secondary codebase.
	 *            "com.pacbio.secondary.analysis.myApp" will only log messages
	 *            from myApp
	 * @param application
	 *            name of the dir under {@link #getLogRoot()}
	 * @param minLevel
	 *            minimum level of logging
	 * @return the logger
	 */
	public Logger initLogger(String root, String application, Level minLevel) {

		// purge parent - this removes the default console handler and cruft
		// from 3rd parties
		Logger logger = Logger.getLogger("");
		for (Handler h : logger.getHandlers()) {
			logger.removeHandler(h);
		}

		LOGGER.fine("Initialize root logger for application " + application);

		logger = Logger.getLogger(root);
		logger.setLevel(minLevel);
		try {
			FileHandler fh = getLogHandler(application);
			logger.addHandler(fh);
		} catch (Exception e) {
			logger.log(Level.SEVERE,
					"Failed to initialize log handler for application "
							+ application, e);
		}
		LOGGER.fine("Root logger initialized for application " + application);
		return logger;
	}

	/**
	 * Get a standard logging file handler that can be set using
	 * {@link java.util.Logging.Logger.addHandler}
	 * 
	 * @param application
	 *            the name of the application; each application will log to its
	 *            own location
	 * @return a logging FileHandler
	 * @throws IOException
	 * @throws SecurityException
	 */
	private FileHandler getLogHandler(String application)
			throws SecurityException, IOException {
		File root = getLogRoot();
		if (root == null)
			throw new IOException("Log root not found: " + root);

		File logDir = new File(root, application);
		if (!logDir.exists() && !logDir.mkdirs())
			LOGGER.log(Level.SEVERE, "Failed to create log folder " + logDir
					+ ". Are permissions set up correctly?");

		// File is like: LOG_DIR/MyApplication/MyApplication.0.log
		File logFile = new File(logDir, application + ".%g.log");

		// Append to log, roll-over up to 5 logs, 10MB each
		FileHandler handler = new FileHandler(logFile.getPath(),
				(1 * 1000 * 1000 * 10), 5, true);
		handler.setFormatter(new SimpleFormatter());
		return handler;
	}

	/**
	 * The absolute path to the directory containing secondary analysis output
	 * for the job.
	 * 
	 * @param jobId
	 */
	public File getJobDirectory(int jobId) {
		String s = String.format("%06d", jobId);
		File parent = new File(getJobsRoot(), s.substring(0, 3));
		if (!parent.exists())
			parent.mkdir();
		return new File(parent, s);
	}

	/**
	 * The absolute path of the common protocols directory.
	 */
	public File getProtocolsRoot() {
		return getAbsolutePath(AbstractAnalysisConfig.PROTOCOL_DIR, new File(
				"common", "protocols"));
	}

	/**
	 * The absolute path of the references root directory.
	 */
	public File getReferencesRoot() {
		return getAbsolutePath(AbstractAnalysisConfig.REFERENCE_DIR, new File(
				"common", "references"));
	}


	/**
	 * 
	 * @return Compatibility matrix
	 */
	public File getCompatibilityMatrix() {
		return resolve(new File("common/etc/compatibilityMatrix.xml"));
	}


	/**
	 * Get an absolute path for the analysis system.
	 * 
	 * @param key
	 *            a path name key from the analysis config file
	 * @param defaultPath
	 *            resolve and return this path if the config file could not be
	 *            found
	 * @return the resolved path or null if undefined or an error occurred
	 */
	private File getAbsolutePath(String key, File defaultPath) {
		String pathName = null;

		AbstractAnalysisConfig config = null;
		try {
			config = getConfig();
			pathName = config.getSystem().getPath(key).getDir();
		} catch (Exception e) {
			pathName = defaultPath == null ? null : defaultPath.getPath();
		}

		File path = null;
		if (pathName != null)
			path = resolve(new File(pathName));
		else
			LOGGER.log(Level.SEVERE,
					"Failed to get absolute path for config file key: " + key);

		return path;
	}

	public abstract AbstractAnalysisConfig getConfig() throws IOException;

	/**
	 * Resolve a path to an absolute path using the analysis root as the current
	 * directory.
	 * 
	 * @param path
	 *            a possibly relative path
	 * @return an absolute path
	 */
	public File resolve(File path) {
		if (path != null && !path.isAbsolute())
			path = new File(getAnalysisRoot(), path.getPath());
		try {
			return path.getCanonicalFile();
		} catch (IOException e) {
			throw new RuntimeException("Failed to get canonical path from File object with path " + path.getAbsolutePath(), e);
		}
	}

	public void reload() {
		// NOOP
	}

}

/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader.io;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

/**
 * Singleton that tracks the current position of the file that is being streamed.
 * Appropriate information can then be extracted when an error occurs.
 * @author jmiller
 * 
 */
public class FastaFileState implements Observer {

	
	//singleton
	private static FastaFileState instance;

	private FastaFileState() {
		reset();
	}

	private long position = -1;

	private long linenum = -1;

	private long currentHeaderLinenum = -1;

	private String path;

	/**
	 * Get an instance
	 * @return FastaFileState instance
	 */
	public static FastaFileState getInstance() {
		if (instance == null)
			instance = new FastaFileState();
		return instance;
	}

	@Override
	public void update(Observable o, Object arg) {

		if (!(arg instanceof Byte))
			return;

		if ((Byte) arg == CharUtils.HEADER_START) {
			currentHeaderLinenum  = linenum;
		}
		
		if ((Byte) arg == CharUtils.LF) {
			//On new line, set position to 0, since we don't want to count that. Ie, the position should 
			//reflect what the user would see if he opens the file in an editor like vi.
			linenum++;
			position = 0;
		} else {
			position++;
		}

	}

 
	/**
	 * Must be called when a new file is opened.
	 */
	private void reset() {
		position = 1;
		linenum = 1;
		currentHeaderLinenum = 1;
	}

	/**
	 * Get information appropriate to an error encountered in sequence.
	 * @return map
	 */
	public String getSequenceInfo() {
		Map<Object, Object> props = new LinkedHashMap<Object, Object>();
		props.put("File",path);
		props.put("LineNumber", this.linenum);
		props.put("Position", this.position);
		return props.toString();
	}

	/**
	 * Get information appropriate to an error encountered in the header.
	 * @return map
	 */
	public String getHeaderInfo() {
		Map<Object, Object> props = new LinkedHashMap<Object, Object>();
		props.put("File",path);
		props.put("HeaderLineNumber", this.currentHeaderLinenum);
		return props.toString();
	}

	/**
	 * Set the name of the file being processed.
	 * @param path
	 */
	public void setFileName(String path) {
		this.path = path;
		reset();
	}

}

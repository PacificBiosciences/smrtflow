/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.pacbio.secondary.common.model.ReferenceIndex;

/**
 * Common cmd logic. Cleanup, logging, etc.
 * @author jmiller
 */
public abstract class CmdBase {
	
	private final static Logger LOGGER = Logger.getLogger(CmdBase.class
			.getName());
	
	protected String refRepos;
	
	
	/**
	 * Execute cmd specific logic.
	 * @throws Exception
	 */
	public abstract void run() throws Exception;
	
	/**
	 * Call in finally() clause.
	 * @param ri
	 */
	protected void unlock(ReferenceIndex ri) {
		if( ri == null )
			return;
		try {
			ri.unlock();
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Unable to unlock index.xml" );
		}
	}
	
	
	/**
	 * The location of the referenceRepository
	 * 
	 * @param refRepos
	 */
	public void setReferenceRepos(String refRepos) {
		this.refRepos = refRepos;
	}

}

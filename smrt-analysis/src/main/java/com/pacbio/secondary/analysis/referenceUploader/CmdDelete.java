/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

import com.pacbio.secondary.common.model.ReferenceIndex;

/**
 * Sets the state of an existing reference to hidden.
 * @author jmiller
 */
public class CmdDelete extends CmdBase {

	private final static Logger LOGGER = Logger.getLogger(CmdDelete.class
			.getName());
	
	
	private String refId;

	/* (non-Javadoc)
	 * @see com.pacbio.secondary.analysis.referenceUploader.CmdBase#run()
	 */
	@Override
	public void run() throws Exception {
		ReferenceIndex ri = null;
		try {
			ri = ReferenceIndex.openForEdit(this.refRepos);
			
			deleteDir();
			
			ri.deleteEntry(refId);
			
			ri.save();
			
			LOGGER.info(this.refId + " has been deleted.");
			
		} finally {
			unlock(ri);
		}
	}
	

	/**
	 * Silently attempt to delete dir
	 */
	private void deleteDir() {
		File f = new File(ReposUtils.getReferenceDirPath(refRepos, refId));
		try {
			FileUtils.deleteDirectory(f);
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Failed to delete " + f, e);
		}
	}


	public void setRefId(String refId) {
		this.refId = refId;
	}


}

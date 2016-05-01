/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.pacbio.secondary.common.model.ReferenceEntry;
import com.pacbio.secondary.common.model.ReferenceEntry.State;
import com.pacbio.secondary.common.model.ReferenceIndex;

/**
 * Sets the state of an existing reference to hidden.
 * @author jmiller
 */
public class CmdHide extends CmdBase {

	private final static Logger LOGGER = Logger.getLogger(CmdHide.class
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
			ReferenceEntry re = ri.getEntry(refId);
			if( re == null ) {
				LOGGER.log(Level.WARNING, "Unable to hide " + refId + ". It does not exist in the index.xml found here: " + refRepos );
			}
			re.setState(State.Inactive);
			ri.save();
		} finally {
			unlock(ri);
		}
	}
	

	public void setRefId(String refId) {
		this.refId = refId;
	}


}

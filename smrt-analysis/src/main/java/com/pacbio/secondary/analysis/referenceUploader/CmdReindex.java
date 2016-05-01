/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.pacbio.secondary.analysis.referenceUploader.io.ContigIndexWriter;
import com.pacbio.secondary.analysis.referenceUploader.io.FastaIndexWriter;
import com.pacbio.secondary.analysis.referenceUploader.io.MultiFileByteBroadcaster;
import com.pacbio.secondary.common.model.ReferenceEntry;
import com.pacbio.secondary.common.model.ReferenceEntry.State;
import com.pacbio.secondary.common.model.ReferenceIndex;

/**
 * Regenerates *.fasta.index *.contig.index. This does not regenerate bwt or sa files.
 * @author jmiller
 */
public class CmdReindex extends CmdBase {

	private final static Logger LOGGER = Logger.getLogger(CmdReindex.class
			.getName());
	
	
	private String refId;


	private String refName;

	/* (non-Javadoc)
	 * @see com.pacbio.secondary.analysis.referenceUploader.CmdBase#run()
	 */
	@Override
	public void run() throws Exception {
		try {
			
			extractRefName();
			
			
			updateIndexXml( ReferenceEntry.State.Processing );
			
			MultiFileByteBroadcaster bb = new MultiFileByteBroadcaster(
					getBroadcasterInput() );
			
			FastaIndexWriter iw = new FastaIndexWriter(ReposUtils
					.getFastaIndexPath(refRepos, refId, refName), 0,
					getFastaLength());
			
			bb.addObserver(iw);
			
			ContigIndexWriter cw = new ContigIndexWriter(ReposUtils
					.getContigIndexPath(refRepos, refId, refName));
			
			iw.addObserver(cw);
			
			bb.start();
			
			
			updateIndexXml( ReferenceEntry.State.Active );
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failed to reindex " + this.refId, e);
			updateIndexXml( ReferenceEntry.State.Failed );
			throw e;
		}
	}
	
	private long getFastaLength() {
		return new File(ReposUtils.getMultiSeqFastaPath(this.refRepos, this.refId, this.refName)).length();
	}

	/**
	 * The reference name is not required on the cmd line, but we can get it indirectly
	 * @throws IOException 
	 */
	private void extractRefName() throws IOException {
		ReferenceIndex ri = ReferenceIndex.open(refRepos);
		this.refName = ri.getEntry(this.refId).getName();
	}


	private List<File> getBroadcasterInput() {
		List<File> l = new ArrayList<File>();
		l.add( new File(ReposUtils.getMultiSeqFastaPath(this.refRepos, this.refId, this.refName)) );
		return l;
	}


	private void updateIndexXml(State state ) throws Exception {
		ReferenceIndex ri = null;
		try {
			ri = ReferenceIndex.openForEdit(refRepos);
			ReferenceEntry re = ri.getEntry( refId );
			
			
			
			re.setState( state );
			re.setLastModified( new Date() );
			ri.save();
		} finally {
			unlock(ri);
		}
	}


	public void setRefId(String refId) {
		this.refId = refId;
	}


}

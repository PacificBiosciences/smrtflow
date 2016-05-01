/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.pacbio.secondary.common.model.Contig;
import com.pacbio.secondary.common.model.Digest;
import com.pacbio.secondary.common.model.ReferenceEntry;
import com.pacbio.secondary.common.model.ReferenceIndex;
import com.pacbio.secondary.common.model.ReferenceInfo;

/**
 * Adds an md5 checksum to the reference in index.xml.
 * 
 * Assumes that a reference.info.xml already exists. This class concatenates a
 * reference's contigs' md5 checksums, then creates a checksum based on that
 * string.
 * 
 * @author jmiller
 */
public class ReferenceChecksum {
	
	private final static Logger LOGGER = Logger.getLogger(CmdUpgrade.class
			.getName());
	
	static final String SEP = System.getProperty("file.separator");

	private String refRepos;
	private String refId;

	/**
	 * The reference to checksum
	 * @param id
	 */
	public ReferenceChecksum(String refId, String refRepos) {
		this.refId = refId;
		this.refRepos = refRepos;
	}

	/**
	 * Add a digest element to the referenceEntry in index.xml. Always add a new checksum.
	 * @throws Exception
	 */
	public void addChecksum() throws Exception {
		ReferenceIndex ri = null;
		
		try {
			ri = ReferenceIndex.openForEdit(refRepos);
			ReferenceEntry re = ri.getEntry(refId);
			
			Digest d = new Digest();
			d.setType("md5");
			d.setValue(  getChecksumOfContigs() );
			
			re.setDigest(d);
			
			ri.save();
			
			
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Problem editing index.xml. repos " + refRepos + ", refid " + refId, e);
			throw e;
			
		} finally {
			ri.unlock();
		}
	}

	private String getChecksumOfContigs() throws IOException, NoSuchAlgorithmException { //throws Exception {
		ReferenceInfo ri = ReferenceInfo.load(new File(this.refRepos + SEP + this.refId ));
		StringBuffer sb = new StringBuffer();
		for( Contig c : ri.getContigs() ) {
			sb.append( c.getDigest().getValue());
		}
		
		MessageDigest md = MessageDigest.getInstance("MD5");
		md.update( sb.toString().getBytes() );
		byte[] d = md.digest();
		BigInteger bi = new BigInteger(1, d);
		return String.format("%1$032x", bi);
	}

	
}

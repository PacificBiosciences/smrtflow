/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader.io;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.pacbio.secondary.analysis.referenceUploader.ReposUtils;
import com.pacbio.secondary.common.model.Contig;
import com.pacbio.secondary.common.model.Digest;

/**
 * Responsible for holding information about a contig as bytes are added.
 * 
 * @author jmiller
 */
public class ContigContainer {

	private final static Logger LOGGER = Logger.getLogger(ContigContainer.class
			.getName());

	private Contig contig = new Contig();

	private List<Byte> headerBytes = new ArrayList<Byte>();


	private MessageDigest digest = null;

	private int contigNum;

	/**
	 * 
	 */
	public ContigContainer() {
		try {
			digest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			LOGGER.log(Level.WARNING, "Unable to perform digest on contig.", e);
		}
	}

	/**
	 * Add a header byte to the header list
	 * 
	 * @param b
	 */
	public void addHeaderByte(byte b) {

		// never write >
		if (b == CharUtils.HEADER_START)
			return;


		this.headerBytes.add(b);
	}

	/**
	 * Get the current contig
	 * 
	 * @return
	 */
	public Contig getContig() {
		return contig;
	}

	/**
	 * 1) Sets contig attributes that must be set only when all contig streaming
	 * is done 2) Closes all resources
	 */
	public void tearDown() {
		setHeader();
		setName();
		setDigest();
	}

	private void setDigest() {
		Digest digest = new Digest();
		this.contig.setDigest(digest);
		byte[] d = this.digest.digest();
		BigInteger lHashInt = new BigInteger(1, d);
		digest.setType("md5");
		// "X" makes the alphabetic characters capitalized. This impl("x") is
		// consistent with the C# code.
		digest.setValue(String.format("%1$032x", lHashInt));
	}

	/**
	 * Sets the displayName attribute of the contig. It is the first 64 chars of the header may not be unique within the reference.
	 */
	private void setName() {
		String name = contig.getHeader();
		if ( name.length() > 64 ) {
			name = name.substring(0, 64);
		}
		contig.setDisplayName( name );
	}

	/**
	 * Set the header field of the contig.
	 */
	private void setHeader() {
		this.contig.setHeader(stringFromByteArray(headerBytes));
		this.contig.setId(ReposUtils.createContigId(this.contigNum));
	}

	/**
	 * Java auto-boxing does not work with typed collections. Why can't you make
	 * a new String( new Byte[]{})???
	 * 
	 * @param l
	 * @return
	 */
	private static String stringFromByteArray(List<Byte> l) {
		int len = l.size();
		byte[] b = new byte[len];
		for (int n = 0; n < len; n++)
			b[n] = l.get(n);
		return new String(b);

	}

	/**
	 * Add byte that comes from fasta sequence
	 * 
	 * @param b
	 */
	public void addSeqByte(byte b) {
		if (b == CharUtils.LF)
			return;
		contig.setLength(contig.getLength() + 1);
		digest.update(b);
	}

	/**
	 * Set the index of the contig. This is what's used when generating the
	 * internal id ref00000N, where N = contigNum.
	 * 
	 * @param contigNum
	 */
	public void setContigNum(int contigNum) {
		this.contigNum = contigNum;
	}

}

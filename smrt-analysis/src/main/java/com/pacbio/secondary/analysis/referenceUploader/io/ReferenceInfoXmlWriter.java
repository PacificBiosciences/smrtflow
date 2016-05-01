/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader.io;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Observable;
import java.util.Set;

import com.pacbio.secondary.common.model.Reference;
import com.pacbio.secondary.common.model.Reference.IndexFileType;
import com.pacbio.secondary.common.model.Reference.Organism;
import com.pacbio.secondary.common.model.ReferenceInfo;

/**
 * This class creates the reference.info.xml content. It attaches to multiSeq
 * writer and monitors sequence information. A reference.info.xml document
 * cannot be created without simultaneously creating a multiSeq fasta.
 * 
 * @author jmiller
 */
public class ReferenceInfoXmlWriter extends AbstractByteObserver {

	private ReferenceInfo refInfo = new ReferenceInfo();

	private String referenceInfoXmlDir;

	private boolean inSeqHeader = true;

	private ContigContainer contigContainer;

	private Set<String> headers = new HashSet<String>();

	/** Key:md5, Value:header **/
	private Map<String, String> md5Hashes = new HashMap<String, String>();

	/**
	 * 
	 * @param cleanedRefName
	 *            the user supplied refName, but spaces and . removed, so it can
	 *            be used as a file name prefix
	 * @param refId
	 * @param referenceInfoXmlDir
	 */
	public ReferenceInfoXmlWriter(String refId, String referenceInfoXmlDir) {

		this.refInfo = initRefInfo(refId);
		this.referenceInfoXmlDir = referenceInfoXmlDir;
		contigContainer = new ContigContainer();
	}

	@Override
	public void update(Observable o, Object arg) {
		super.update(o, arg);
		if (observableIsFinished())
			return;

		if (inSeqHeader)
			handleSeqHeaderByte((Byte) arg);
		else
			handleFastaSeqByte((Byte) arg);
	}

	private void handleSeqHeaderByte(byte b) {
		if (b == CharUtils.LF) {
			inSeqHeader = false;

			return;
		}
		this.contigContainer.addHeaderByte(b);
	}

	private void handleFastaSeqByte(byte b) {
		if (b == CharUtils.HEADER_START) {
			inSeqHeader = true;
			// when we get to a new contig while streaming through sequence,
			// need to flush the old
			flushLastContig(true);
			return;
		}
		this.contigContainer.addSeqByte(b);
	}

	private void flushLastContig(boolean createNew) {

		contigContainer.setContigNum(refInfo.getContigs().size() + 1);
		contigContainer.tearDown();

		headerIsLegal();
		contigMD5IsUnique();
		
		if (contigContainer.getContig().getLength() == 0)
			//bug 24299
			throw new FastaSequenceException(String.format(
					"Invalid empty contig: %s", 
					contigContainer.getContig().getHeader()));
			

		if (contigContainer.getContig().getLength() > refInfo.getReference()
				.getMaxContigLength())
			refInfo.getReference().setMaxContigLength(
					contigContainer.getContig().getLength());

		refInfo.getContigs().add(contigContainer.getContig());

		if (createNew)
			contigContainer = new ContigContainer();
	}

	/**
	 * Check the header for an id
	 * 
	 * @param header
	 * @return true if the header contains a pacbio-style id
	 */
	protected boolean hasIdPatternMatch(String header) {
		return header.matches(".*ref[0-9]{6}.*");
	}

	/**
	 * Check the header for trailing whitespace
	 * 
	 * @param header
	 * @return true if the header contains trailing whitespace
	 */
	protected boolean hasTrailingWhitespace(String header) {
		return header.matches(".+\\s+");
	}

	/**
	 * Throw an exception if the header has already been encountered in the
	 * file.
	 * 
	 * @param header
	 */
	private void headerIsLegal() {
		String header = contigContainer.getContig().getHeader();
		if (header.isEmpty())
			throw new FastaHeaderException("Encountered an empty header");

		if (hasIdPatternMatch(header))
			throw new FastaHeaderException(
					"the header contains a PacBio internal id of the form refNNNNNN, where N is a digit: "
							+ header);

		if (hasTrailingWhitespace(header))
			throw new FastaHeaderException(
					"the header contains trailing whitespace " + header);

		if (this.headers.contains(header))
			throw new FastaHeaderException(header + " already exists.");
		this.headers.add(header);
	}

	/**
	 * Throw a {@link FastaSequenceException} if a contig exists which has the
	 * same md5 checksum as the current contig.
	 */
	private void contigMD5IsUnique() {
		String md5 = contigContainer.getContig().getDigest().getValue();
		String existingHeader = this.md5Hashes.get(md5);
		String currHeader = contigContainer.getContig().getHeader();
		if (existingHeader != null)
			throw new FastaSequenceException(String.format(
					"Illegal state: duplicate sequences digests %s. "
							+ "These contigs are identical:\n%s\n%s\n", md5,
					currHeader, existingHeader));
		md5Hashes.put(md5, currHeader);

	}

	/**
	 * Create basic objects within referenceInfo
	 * 
	 * @param refId
	 * @return
	 */
	private ReferenceInfo initRefInfo(String refId) {
		refInfo = new ReferenceInfo();
		refInfo.setId(refId);
		refInfo.setReference(new Reference());
		refInfo.setOrganism(new Organism());
		return refInfo;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.pacbio.secondary.analysis.referenceUploader.io.AbstractByteObserver
	 * #finish()
	 */
	@Override
	public void finish() {
		try {
			flushLastContig(false);
			refInfo.getReference().setNumContigs(refInfo.getContigs().size());
			refInfo.save(new File(referenceInfoXmlDir));
		} catch (IOException e) {
			throw new RuntimeException("Unable to save " + referenceInfoXmlDir,
					e);
		}

	}
	
	@Override
	public void finishInError() {
		try {
			refInfo.getReference().setNumContigs(refInfo.getContigs().size());
			refInfo.save(new File(referenceInfoXmlDir));
		} catch (IOException e) {
			throw new RuntimeException("Unable to save " + referenceInfoXmlDir,
					e);
		}
		
	}
	

	/***
	 * Typically, in our system, you would supply ./sequence/some.fasta Sets the
	 * value of {@link Reference#getFile()}
	 * 
	 * @param string
	 */
	public void setMultiSeqFastaValue(String multiSeqFasta) {
		com.pacbio.secondary.common.model.Reference.File f = new com.pacbio.secondary.common.model.Reference.File();
		f.setValue(multiSeqFasta);
		f.setFormat("text/fasta");
		this.refInfo.getReference().setFile(f);
	}

	public void addIndexFile(IndexFileType type, String file) {
		com.pacbio.secondary.common.model.Reference.IndexFile f = new com.pacbio.secondary.common.model.Reference.IndexFile();
		f.setType(type.toString());
		f.setValue(file);
		this.refInfo.getReference().addIndexFile(f);

	}

	/**
	 * Set cmd line arg
	 * 
	 * @param desc
	 */
	public void setDescription(String desc) {
		this.refInfo.getReference().setDescription(desc);
	}

	/**
	 * Set cmd line arg
	 * 
	 * @param type
	 */
	public void setType(String type) {
		this.refInfo.getReference().setType(type);
	}

	/**
	 * Set the software version
	 * 
	 * @param version
	 */
	public void setVersion(String version) {
		this.refInfo.setVersion(version);
	}

	public Organism getOrganism() {
		return this.refInfo.getOrganism();
	}

	

	//
	// public void setPloidy(String ploidy) {
	// this.refInfo.getOrganism().setPloidy(ploidy);
	//
	// }
}

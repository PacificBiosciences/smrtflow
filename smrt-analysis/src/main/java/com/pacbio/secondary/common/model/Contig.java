package com.pacbio.secondary.common.model;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * A contig element in a reference.index.xml file.
 */
public class Contig {
	private String id;
	private int length;
	private String displayName;
	private Digest digest;
	private String header;
	
	@XmlAttribute
	public void setId(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	@XmlAttribute
	public int getLength() {
		return length;
	}

	public void setLength(int length) {
		this.length = length;
	}

	@XmlAttribute
	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public Digest getDigest() {
		return digest;
	}

	public void setDigest(Digest digest) {
		this.digest = digest;
	}

	public String getHeader() {
		return header;
	}

	public void setHeader(String header) {
		this.header = header;
	}

}

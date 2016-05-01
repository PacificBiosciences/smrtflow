package com.pacbio.secondary.common.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlValue;

/**
 * A reference entry in a reference.info.xml file.
 */
public class Reference {

	public enum IndexFileType {
		Sa {
			@Override
			public String toString() {
				return "sawriter";
			}
		},
		Indexer {
			@Override
			public String toString() {
				return "indexer";
			}
		},
		GatkDict {
			@Override
			public String toString() {
				return "gatk_dict";
			}
		},
		SamIdx {
			@Override
			public String toString() {
				return "sam_idx";
			}
		},
        // MK. Add IndexFastaContig
        FastaContigIndex  {
            @Override
            public String toString() {
                return "fasta_contig_index";
            }
        }
	}

	private int numContigs;
	private int maxContigLength;
	private File file;
	private List<IndexFile> indexFiles = new ArrayList<IndexFile>();
	private String description;
	private String type;
	private Organism organism;

	/**
	 * Information about the reference organism
	 */
	public static class Organism {
		
		private String name;
		private String ploidy;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getPloidy() {
			return ploidy;
		}

		public void setPloidy(String ploidy) {
			this.ploidy = ploidy;
		}

	}

	/**
	 * Info about the reference sequence file.
	 */
	public static class File {
		private String format;
		private String value;

		@XmlAttribute
		public String getFormat() {
			return format;
		}

		public void setFormat(String format) {
			this.format = format;
		}

		@XmlValue
		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

	/**
	 * Info about the index for the reference.
	 */
	public static class IndexFile {
		private String type;
		private String value;

		@XmlAttribute
		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		@XmlValue
		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

	@XmlElement(name = "num_contigs")
	public int getNumContigs() {
		return numContigs;
	}

	public void setNumContigs(int numContigs) {
		this.numContigs = numContigs;
	}

	@XmlElement(name = "max_contig_length")
	public int getMaxContigLength() {
		return maxContigLength;
	}

	public void setMaxContigLength(int maxContigLength) {
		this.maxContigLength = maxContigLength;
	}

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	/**
	 * Convenience method to get an index file by type
	 * 
	 * @param type
	 * @return
	 */
	public IndexFile getIndexFile(IndexFileType type) {
		for (IndexFile i : getIndexFiles()) {
			if (i.type.equals(type.toString()))
				return i;
		}
		return null;
	}

	public void addIndexFile(IndexFile f) {
		indexFiles.add(f);
	}

	@XmlElement(name = "index_file")
	protected List<IndexFile> getIndexFiles() {
		return indexFiles;
	}

	protected void setIndexFiles(List<IndexFile> indexFiles) {
		this.indexFiles = indexFiles;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

}

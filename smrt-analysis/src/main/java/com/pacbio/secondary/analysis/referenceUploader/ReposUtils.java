/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader;

import java.io.File;

import com.pacbio.secondary.common.model.ReferenceInfo;

/**
 * Utility class pertaining to dir structure of the repository.
 * 
 * @author jmiller
 */
public class ReposUtils {

	private static final String SEP = System.getProperty("file.separator");

	/**
	 * Get the absolute path to the multiSeq fasta file
	 */
	public static String getMultiSeqFastaPath(String repos, String refId,
			String refName) {
		return new File(repos + SEP + refId + SEP + "sequence" + SEP
				+ nameToFileName(refName) + ".fasta").getAbsolutePath();
	}

	/**
	 * Provides the "repository view". Ie, ./sequence/foo.fasta
	 * 
	 * @param refId
	 * @return
	 */
	public static String getMultiSeqFastaPath(String refName) {
		return "." + SEP + "sequence" + SEP + nameToFileName(refName)
				+ ".fasta";
	}

	/**
	 * Get the absolute path to the dir that reference.info.xml live in. Note
	 * that this is because {@link ReferenceInfo#save(File)} expects the dir,
	 * and supplies the file name itself.
	 * 
	 * @param repos
	 * @param refId
	 * @return
	 */
	public static String getReferenceDirPath(String repos, String refId) {
		return new File(repos + SEP + refId).getAbsolutePath();
	}

	/**
	 * Provides the "repository view". Ie, ./refId/reference.info.xml
	 * 
	 * @param refId
	 * @return
	 */
	public static String getReferenceInfoXmlPath(String refId) {
		return "." + SEP + refId + SEP + ReferenceInfo.FILE_NAME;
	}

	/**
	 * Get the absolute path to the fasta index file
	 * 
	 * @param repos
	 * @param refId
	 * @return
	 */
	public static String getFastaIndexPath(String repos, String refId,
			String refName) {
		return new File(repos + SEP + refId + SEP + "sequence" + SEP
				+ nameToFileName(refName) + ".fasta.index").getAbsolutePath();
	}

	/**
	 * Provides the "repository view". Ie, ./refId/sequence/fpp.fasta.index
	 * 
	 * @param refId
	 * @param refName
	 * @return
	 */
	public static String getFastaIndexPath(String refName) {
		return "." + SEP + "sequence" + SEP + nameToFileName(refName)
				+ ".fasta.index";
	}

	/**
	 * Provides the "repository view". Ie, ./refId/sequence/fpp.dict
	 * 
	 * @param refName
	 * @return
	 */
	public static String getGatkDictPath(String refName) {
		return "." + SEP + "sequence" + SEP + nameToFileName(refName) + ".dict";
	}

	/**
	 * Provides the "repository view". Ie, ./refId/sequence/fpp.dict
	 * 
	 * @param refName
	 * @return
	 */
	public static String getSamIdxPath(String refName) {
		return "." + SEP + "sequence" + SEP + nameToFileName(refName) + ".fasta.fai";
	}

    // MK. Explicitly add Fasta Contig Index
    public static String getFastaContigIndex(String refName) {
        return "." + SEP + "sequence" + SEP + nameToFileName(refName) + ".fasta.contig.index";
    }
	/**
	 * Get the abs path to the sa index file
	 * 
	 * @param refRepos
	 * @param refId
	 * @param refName
	 * @return
	 */
	public static String getSaIndexPath(String repos, String refId,
			String refName) {
		return new File(repos + SEP + refId + SEP + "sequence" + SEP
				+ nameToFileName(refName) + ".fasta.sa").getAbsolutePath();
	}

	/**
	 * Get the abs path to the sequence dir
	 * 
	 * @param refRepos
	 * @param refId
	 * @param refName
	 * @return
	 */
	public static String getSequenceDirPath(String repos, String refId) {
		return new File(repos + SEP + refId + SEP + "sequence")
				.getAbsolutePath();
	}

	/**
	 * Provides the "repository view". Ie, ./refId/sequence/fpp.fasta.sa
	 * 
	 * @param refId
	 * @param refName
	 * @return
	 */
	public static String getSaIndexPath(String refName) {
		return "." + SEP + "sequence" + SEP + nameToFileName(refName)
				+ ".fasta.sa";
	}

	/**
	 * Get the absolute path to the index file
	 * 
	 * @param refRepos
	 * @param refId
	 * @return
	 */
	public static String getContigIndexPath(String repos, String refId,
			String refName) {
		return new File(repos + SEP + refId + SEP + "sequence" + SEP
				+ nameToFileName(refName) + ".fasta.contig.index")
				.getAbsolutePath();
	}

	/**
	 * Get a PacBio contigId for a contig number. The id will not be longer than
	 * 9 characters, and must start at 1.
	 * 
	 * @param contigNum
	 * @return a contigId
	 */
	public static String createContigId(int num) {
		if (num < 1 || num > 999999) {
			throw new IllegalArgumentException(
					"param must between 1 and 999999: " + num);
		}
		String s = Integer.toString(num);
		int sLen = s.length();
		StringBuffer sb = new StringBuffer("ref");
		for (int n = 0; n <= 5 - sLen; n++) {
			sb.append("0");
		}
		sb.append(s);
		return sb.toString();

	}

	/**
	 * Replace spaces and any non-alphanumeric characters with underscores.
	 * 
	 * @param refName
	 * @return file name compliant string
	 */
	public static String nameToFileName(String refName) {
		char[] cp = new char[refName.length()]; 
		char[] orig = refName.toCharArray();
		for( int n = 0; n < orig.length; n++  ) {
			Character c = orig[n];
			if (!Character.isLetterOrDigit(c)) {
				cp[n] = '_';
			} else {
				cp[n] = c;
			}
		}
		return new String( cp );
	}

	/**
	 * Get an absolute path to a resource represented by the "./*" format in the
	 * metadata
	 * 
	 * @param refRepos
	 *            abs path to repos
	 * @param refId
	 *            the refid
	 * @param refLocalValue
	 *            the way we show paths in the metadata, ie
	 *            ./sequence/my.fasta.index
	 * @return
	 */
	public static String getAbsPath(String refRepos, String refId,
			String refLocalValue) {
		if (!refLocalValue.startsWith("./")) {
			throw new IllegalArgumentException(
					"Expecting something of the format ./sequence/foo.fasta.sa");
		}
		return new File(refRepos + SEP + refId + SEP
				+ refLocalValue.replaceFirst("./", "")).getAbsolutePath();
	}

//	/**
//	 * This is only used for upgrading the repository. Reverse engineer the name
//	 * from the directory, since the name is lost once a reference has been
//	 * created.
//	 * 
//	 * @param refRepos
//	 * @param id
//	 * @return null if the fasta file is missing
//	 */
//	public static String getReferenceName(String refRepos, String id) throws IllegalStateException {
//			File seqDir = new File(getSequenceDirPath(refRepos, id));
//			if( !seqDir.exists() )
//				throw new IllegalStateException( seqDir + " does not exist!");
//			File[] list = seqDir.listFiles(new FileFilter() {
//				@Override
//				public boolean accept(File pathname) {
//					return pathname.getPath().endsWith(".fasta");
//				}
//			});
//			if( list == null || list.length == 0)
//				throw new IllegalStateException( "No .fasta files under " + seqDir );
//			
//			String fName = list[0].getName();
//			String noExt = fName.replace(".fasta", "");
//			return noExt.replace('_', ' ');
//
//	}

}

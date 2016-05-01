/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader.io;

/**
 * This exception should be thrown when an error in a contig header is encountered.
 * The message is automatically append with the following information:
 * - name of the fasta file
 * - line number of the offending header
 * @author jmiller
 */
public class FastaHeaderException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8644292514114723977L;

	/**
	 * @param message
	 */
	public FastaHeaderException(String message) {
		super(message + getFastaFileState() );
	}
	
	
	/**
	 * @param message
	 */
	public FastaHeaderException(String message, Throwable throwable) {
		super(message + getFastaFileState(), throwable );
	}
	

	private static String getFastaFileState() {
		return " " + FastaFileState.getInstance().getHeaderInfo();
	}


	

}

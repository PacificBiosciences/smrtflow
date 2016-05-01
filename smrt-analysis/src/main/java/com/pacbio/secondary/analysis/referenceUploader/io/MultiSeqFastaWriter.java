/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Observable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

import com.pacbio.secondary.analysis.referenceUploader.Constants;

/**
 * Observer component responsible for write the multi-seq fasta file.
 * 
 * @author jmiller
 */
public class MultiSeqFastaWriter extends ObservableObserver {

	private final static Logger LOGGER = Logger
			.getLogger(MultiSeqFastaWriter.class.getName());

	private BufferedWriter outputStream;
	private byte currentByte;

	// indicates whether the current byte comes from the >header line
	private boolean inSeqHeader = true;

	// store as member for logging
	private String multiSeqPath;

	private int columnIndex = 0;

	private int lineLength;


	/**
	 * Makes all required parent dirs of the output fasta file. Creates a fasta
	 * file where the sequence line length defaults to 60 chars.
	 * 
	 * @param multiSeqPath
	 *            Full path to the output file
	 * @throws FileNotFoundException
	 */
	public MultiSeqFastaWriter(String multiSeqPath) throws IOException {
		this(multiSeqPath, Constants.FASTA_LINE_LENGTH);

	}

	/**
	 * Makes all required parent dirs of the output fasta file. Creates a fasta
	 * file where the sequence line length is configurable.
	 * 
	 * @param multiSeqPath
	 * @param lineLength
	 * @throws IOException
	 */
	public MultiSeqFastaWriter(String multiSeqPath, int lineLength)
			throws IOException {
		this.multiSeqPath = multiSeqPath;
		this.lineLength = lineLength;
		File fasta = new File(multiSeqPath);
		File parent = fasta.getParentFile();
		if (!parent.exists())
			if (!parent.mkdirs())
				throw new IOException(
						"Failed to create output fasta directory: " + parent);
		this.outputStream = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(fasta), "UTF8"));
	}

	@Override
	public void update(Observable o, Object arg) {
		super.update(o, arg);
		if (observableIsFinished())
			return;

		try {
			currentByte = (Byte) arg;

			handleByte();
			
		} catch (RuntimeException e) {
			throw e;

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Do something with the current byte.
	 * 
	 * @throws IOException
	 */
	private void handleByte() throws IOException {

		// Unix,MacOSX = LF
		// Win = CR+LF
		// MacOS9 = CR
		// So, we can safely ignore CR chars, except for MacOS9, which we are
		// not supporting
		if (currentByte == CharUtils.CR)
			return;

		if (inSeqHeader)
			handleSeqHeaderByte();
		else
			handleFastaSeqByte();
	}

	/**
	 * Handle bytes that are encountered within sequence.
	 * 
	 * @throws IOException
	 */
	private void handleFastaSeqByte() throws IOException {
		byte toWrite = CharUtils.getContigIupacByte(this.currentByte);

		if (toWrite == CharUtils.NOT_IUPAC)
			throw new FastaSequenceException(
					"Encountered non-IUPAC symbol in fasta sequence: "
							+ CharUtils.getChar(currentByte) + ". Terminating.");

		if (toWrite == CharUtils.HEADER_START) {
			// we've run into a new contig.
			inSeqHeader = true;

			// checkWriteNewLine();
			writeByte(CharUtils.LF);
			writeByte(toWrite);
			return;
		}

		// intercept and ignore lineFeeds within seq
		if (toWrite == CharUtils.LF)
			return;

		if (columnIndex == lineLength) {
			writeByte(CharUtils.LF);
			columnIndex = 0;
		}

		writeByte(toWrite);
		columnIndex++;

	}

	/**
	 * Handle bytes that come directly from a header line. Header bytes can be
	 * written directly to the output stream. Tabs are not allowed.
	 * 
	 * @throws IOException
	 */
	private void handleSeqHeaderByte() throws IOException {

		// All the validation is now done using the scala pre-validate
//		if( currentByte == CharUtils.TAB )
//			throw new FastaHeaderException(
//					"The tab character is not allowed in the header. Terminating. " );
//
//		if( currentByte == CharUtils.COLON )
//			throw new FastaHeaderException(
//					"':' character is not allowed in the header. Terminating. " );
//
//		if( currentByte == CharUtils.DOUBLE_QUOTE )
//			throw new FastaHeaderException(
//					"'\"' character is not allowed in the header. Terminating. " );
		
		if( currentByte == CharUtils.HEADER_START && columnIndex != 0 )
			throw new FastaHeaderException(
					"Illegal > character in header. It may only exist at the beginning of the header line. " );
		
		if (currentByte == CharUtils.LF) {
			// at the end of the header line, write the NL char, and set boolean
			writeByte();
			inSeqHeader = false;
			// reset this columnIndex so that when a fasta seq resumes, the correct # chars are written per line
			columnIndex = 0;
			return;
		}
		writeByte();
		
		//only keeping track of the column idx in the header to we can fail on a > in the wrong position
		columnIndex++;
	}

	/**
	 * Write a byte that was broadcasted by the observable element
	 * 
	 * @throws IOException
	 */
	private void writeByte() throws IOException {
		writeByte(currentByte);
	}

	/**
	 * Write a non-broadcasted byte or a converted byte
	 * 
	 * @param b
	 * @throws IOException
	 */
	private void writeByte(byte b) throws IOException {
		outputStream.write(b);
		super.setChanged();
		super.notifyObservers(b);
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.pacbio.secondary.analysis.referenceUploader.io.ByteObserver#Finish()
	 */
	@Override
	public void finish() {
		try {
			// Marco requested that multiSeq fasta files end with NL
			writeByte(CharUtils.LF);
			outputStream.flush();
			LOGGER.log(Level.INFO, "Successfully finished writing " + multiSeqPath);
		} catch (IOException e) {
			throw new RuntimeException(e);

		} finally {
			// in the role of Observable, send a shout out to our listeners
			super.setChanged();
			super.notifyObservers(ObservableByteBroadcaster.Signal.Finished);
			IOUtils.closeQuietly(outputStream);
			
		}
		
	}

	@Override
	public void finishInError() {
		try {
			outputStream.flush();
		} catch (IOException e) {
			throw new RuntimeException(e);
			
		} finally {
			// in the role of Observable, send a shout out to our listeners
			super.setChanged();
			super.notifyObservers(ObservableByteBroadcaster.Signal.Error);
			IOUtils.closeQuietly(outputStream);
		}
		
	}

}

/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Observable;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

/**
 * As inputs, accepts a Fasta file, and a couple of indexing params. Produces an
 * index file. It typically attaches to a MultiSeqFastaWriter instance, and does
 * something with each fired byte. If the byte comes from the header, it gets
 * written directly to the output stream. Else, is used as part of index calc.
 * 
 * It also serves as an observable entity. A contig index writer may listen to
 * its broadcast.
 * 
 * @author jmiller
 */
public class FastaIndexWriter extends ObservableObserver {

	private final static Logger LOGGER = Logger.getLogger(FastaIndexWriter.class.getName());

	private long fastaSliceSize;
	private long srcFileLength;
	private long maxChunkSize;

	private BufferedWriter outputStream;

	private byte currentByte;

	private boolean inSeqHeader = true;

	private long sequencePosition = 1;

	private long filePosition = 0;

	/** pair reflecting the file pos [1] of the last seq position [0] **/
	private Long[] positionPair = { null, null };

	private String outputIndexFile;

	/**
	 * Constructor - use this if you want to configure the fastaSliceSize to be
	 * something other than 2000
	 * 
	 * @param outputIndexFile
	 *            Index file to write out
	 * @param initialMaxChunkSize
	 *            Called initial* because you can pass in 0, and maxChunkSize
	 *            will by calculated as a % of file size
	 * @param srcFileLength
	 *            ByteCount of original fasta file(s)
	 * @param fastaSliceSize
	 *            ByteCount of slices
	 * @throws IOException
	 */
	public FastaIndexWriter(String outputIndexFile,
			long initialMaxChunkSize,
			long srcFileLength,
			long fastaSliceSize) throws IOException {

		// set as member for logging purposes only
		this.outputIndexFile = outputIndexFile;

		this.srcFileLength = srcFileLength;
		this.fastaSliceSize = fastaSliceSize;

		File index = new File(outputIndexFile);
		File parent = index.getParentFile();
		if (!parent.exists())
			parent.mkdirs();
		this.outputStream = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(index), "UTF8"));

		init(initialMaxChunkSize);
	}

	/**
	 * Constructor - use this if you want to configure the fastaSliceSize to be
	 * something other than 2000 {@link FastaIndexWriter}
	 * 
	 * @param outputIndexFile
	 * @param initialMaxChunkSize
	 * @param srcFileLength
	 * @throws IOException
	 */
	public FastaIndexWriter(String outputIndexFile,
			long initialMaxChunkSize,
			long srcFileLength) throws IOException {
		this(outputIndexFile, initialMaxChunkSize, srcFileLength, 2000);
	}

	/**
	 * Set the chunk size, based on the source file length
	 * 
	 * @param initialMaxChunkSize
	 */
	private void init(long initialMaxChunkSize) {
		if (initialMaxChunkSize == 0) {
			maxChunkSize = fastaSliceSize
					* ((srcFileLength > (1024 * 1024 * 1024)) ? 20
							: (srcFileLength > (500 * 1024 * 1024)) ? 10 : 1);
		} else {
			maxChunkSize = initialMaxChunkSize;
		}

	}

	@Override
	public void update(Observable o, Object arg) {
		super.update(o, arg);
		if (observableIsFinished())
			return;
		

		try {
			currentByte = (Byte) arg;

			handleByte();

		} catch (Exception e) {
			// this is a fatal exception, so call finish
			finish();
			throw new RuntimeException(e);
		}
	}

	private void handleByte() throws IOException {
		if (inSeqHeader)
			handleSeqHeaderByte();
		else
			handleFastaSeqByte();
		filePosition++;
	}

	/**
	 * Handle a byte that originates in sequence. Ignore new line bytes, since
	 * they are not involved in index calcuation.
	 * 
	 * @throws IOException
	 */
	private void handleFastaSeqByte() throws IOException {

		if (currentByte == CharUtils.LF)
			// with the fasta seq, ignore linefeeds
			return;

		if (currentByte == CharUtils.HEADER_START) {
			// ran into a new contig, so: 1) write last contig index line, write
			// the header start byte, and toggle the flag

			writeIndexLine(true);

			writeByte();

			inSeqHeader = true;
			return;
		}
		//non-boundary case of base pair byte within sequence
		trapState();
		
		if( doSliceIndex() )
			writeIndexLine(true);
			
		sequencePosition++;
	}

	/**
	 * @return true	if we've gotten through a slice and need to index it
	 */
	private boolean doSliceIndex() {
		return (sequencePosition%maxChunkSize)==0 && srcFileLength > filePosition+1;
	}

	/**
	 * Handle a byte that originates in the header.
	 * @throws IOException
	 */
	private void handleSeqHeaderByte() throws IOException {
		if (currentByte == CharUtils.LF) {
			//boundary case where the header ends
			//1)write the line feed.
			writeByte();
			//2)spit out the first index line of the new contig
			writeFirstIndexLineOfContig();
			//3)toggle state
			sequencePosition = 1;
			inSeqHeader = false;
			return;
		}
		//non-boundary case, just write the header
		writeByte();
	}

	/**
	 * Store the file position of the last base
	 */
	private void trapState() {
		positionPair[0] = this.sequencePosition;
		positionPair[1] = this.filePosition;
	}

	
	/**
	 * Write an index line, which is the current (trapped) seq position mapped to its file position.
	 * @param writeTerminatingLineFeed
	 * @throws IOException
	 */
	private void writeIndexLine(boolean writeTerminatingLineFeed)
			throws IOException {
		if (this.positionPair[0] == null)
			//bug 24299 - fasta file would have to contain ONLY a header for this to happen
			throw new FastaSequenceException("Invalid empty contig");
		byte[] bytes = Long.toString(this.positionPair[0]).getBytes();
		writeBytes(bytes);
		writeByte(CharUtils.TAB);
		bytes = Long.toString(this.positionPair[1]).getBytes();
		writeBytes(bytes);
		if (writeTerminatingLineFeed)
			writeByte(CharUtils.LF);
	}

	/**
	 * When we get to a LF char in the header string, an index line needs to be
	 * written that denotes the beginning of the contig. So, the sequencePos
	 * will be 1. The filePosition values needs to be bumped by 1, since we're
	 * still on the LF postion, not the actual seq start.
	 * 
	 * @throws IOException
	 */
	private void writeFirstIndexLineOfContig() throws IOException {
		byte[] bytes = Long.toString(1).getBytes();
		writeBytes(bytes);
		writeByte(CharUtils.TAB);
		bytes = Long.toString(this.filePosition + 1).getBytes();
		writeBytes(bytes);
		writeByte(CharUtils.LF);
	}

	/**
	 * Write byte array
	 * @param bytes
	 * @throws IOException
	 */
	private void writeBytes(byte[] bytes) throws IOException {
		for (byte b : bytes)
			writeByte(b);
	}

	/**
	 * Write the current byte obtained from update
	 * @throws IOException
	 */
	private void writeByte() throws IOException {
		writeByte(currentByte);
	}

	/**
	 * The core write method that all writeByte* methods must call, since it does observer notification.
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
			callWriteLastIndexLineOfContig();
		}  finally {
			IOUtils.closeQuietly(outputStream);
			// in the role of Observable, send a shout out to our listeners
			super.setChanged();
			super.notifyObservers(ObservableByteBroadcaster.Signal.Finished);
		}
		
	}

	/**
	 * This exception needs to propagate up the stack, so the reference entry is
	 * not activated.
	 */
	private void callWriteLastIndexLineOfContig() {
		try {
			writeIndexLine(false);
		} catch (IOException e) {
			LOGGER.warning("Failure writing final index line of: "
					+ outputIndexFile);
		}

	}

	@Override
	public void finishInError() {
		IOUtils.closeQuietly(outputStream);
		super.setChanged();
		super.notifyObservers(ObservableByteBroadcaster.Signal.Error);
		
	}

}

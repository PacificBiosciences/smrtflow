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

import org.apache.commons.io.IOUtils;

import com.pacbio.secondary.analysis.referenceUploader.ReposUtils;

/**
 * @author jmiller
 */
public class ContigIndexWriter extends AbstractByteObserver {
	
	
	
	private BufferedWriter outputStream;
	
	private byte currentByte;

	private boolean inSeqHeader = true;

	private long filePosition;

	private long headerStartPosition;

	private int contigCount = 1;

	public ContigIndexWriter(String contigIndexPath) throws IOException {
		
		File index = new File(contigIndexPath);
		File parent = index.getParentFile();
		if (!parent.exists())
			parent.mkdirs();
		this.outputStream = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(index), "UTF8"));
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
			finishInError();
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


	private void handleFastaSeqByte() throws IOException {
		if (currentByte == CharUtils.HEADER_START) {
			writeByte();
			contigCount++;
			headerStartPosition = filePosition;
			inSeqHeader = true;
		}
	}


	private void handleSeqHeaderByte() throws IOException {
		if (currentByte == CharUtils.LF) {
			//boundary case where the header ends
			//1)write the line feed.
			writeByte();
			//2)spit out the position of >
			writeFirstIndexLine();
			inSeqHeader = false;
			return;
		}
		//non-boundary case, just write the header
		writeByte();
		
	}


	private void writeFirstIndexLine() throws IOException {
		byte[] bytes = ReposUtils.createContigId(contigCount).getBytes();
		writeBytes(bytes);
		writeByte(CharUtils.TAB);
		bytes = Long.toString(this.headerStartPosition).getBytes();
		writeBytes(bytes);
		writeByte(CharUtils.LF);
	}


	private void writeBytes(byte[] bytes) throws IOException {
		for (byte b : bytes)
			writeByte(b);
	}


	private void writeByte() throws IOException {
		writeByte(currentByte);
		
	}


	private void writeByte(byte b) throws IOException {
		outputStream.write(b);
		
	}


	/* (non-Javadoc)
	 * @see com.pacbio.secondary.analysis.referenceUploader.io.AbstractByteObserver#finish()
	 */
	@Override
	public void finish() {
		try {
			outputStream.flush();
		} catch (IOException e) {

		} finally {
			IOUtils.closeQuietly(outputStream);
		}
	}


	@Override
	public void finishInError() {
		finish();
	}


}

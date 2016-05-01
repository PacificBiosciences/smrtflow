/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import org.apache.commons.io.IOUtils;

/**
 * An object of this type can stream through an arbitrary number of files,
 * broadcasting each byte, until finished. No information is broadcast about
 * transitions between files.
 * 
 * @author jmiller
 */
public class MultiFileByteBroadcaster extends ObservableByteBroadcaster {

	private List<File> fastaFiles;

	/***
	 * Construct with the files to stream through.
	 * 
	 * @param fastaFiles
	 */
	public MultiFileByteBroadcaster(List<File> fastaFiles) {
		this.fastaFiles = fastaFiles;
	}

	/**
	 * Start file streaming. Ends when the end of the last file has been
	 * reached.
	 * 
	 * @throws IOException
	 */
	public void start() throws IOException {
//		try {
//			for (File f : fastaFiles) {
//				stream(f);
//			}
//
//		} finally {
//			notifyFinished();
//		}
		
		try {
			for (File f : fastaFiles) {
				stream(f);
			}
			
			notifyFinished();

		} catch (Exception e ) {
			
			notifyError();
			
			if (e instanceof RuntimeException) 
				throw (RuntimeException) e;
			
			throw new RuntimeException(e);
			
			
		}
		
	}

	/**
	 * Notify listeners that streaming has stopped.
	 */
	private void notifyFinished() {
		this.setChanged();
		notifyObservers(ObservableByteBroadcaster.Signal.Finished);
	}
	
	
	/**
	 * Notify listeners that streaming has stopped with error conditions.
	 */
	private void notifyError() {
		this.setChanged();
		notifyObservers(ObservableByteBroadcaster.Signal.Error);
	}
	

	/**
	 * Stream through the file, calling notifyObservers on each new byte..
	 * 
	 * @param f
	 * @throws IOException
	 */
	private void stream(File f) throws IOException {
		//Set up a mechanism to track exactly where the input file contains error(s)
		FastaFileState ffs = FastaFileState.getInstance();
		ffs.setFileName(  f.getPath() );
		this.addObserver( ffs );
		
		BufferedReader in = null;
		try {
			boolean first = true;
			in = new BufferedReader( new InputStreamReader(new FileInputStream(f), "UTF8") );
			while (true) {
				int i = in.read();
				if (i == -1)
					break;
				
				if( first ) {
					if( i == CharUtils.BOM )
						//BOM's inserted at beginning of file by windows apps should be silently ignored when read
						continue;
					if (i != CharUtils.HEADER_START)
						//anything else should cause exception
						throw new FastaHeaderException( "Not a valid fasta file. It does not start with >.");
				}
				this.setChanged();
				this.notifyObservers((byte) i);
				first = false;
			}

		} finally {
			IOUtils.closeQuietly(in);
		}
	}

}

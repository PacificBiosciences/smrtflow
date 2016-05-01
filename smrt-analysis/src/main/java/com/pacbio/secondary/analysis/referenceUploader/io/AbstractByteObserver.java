/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader.io;

import java.util.Observable;

/**
 * An entity in the system that only wants to listen to bytes should extend
 * this. It will automatically call the implementing entity's finish() method when
 * the observable object finishes.
 * 
 * @author jmiller
 */
public abstract class AbstractByteObserver implements ByteObserver {

	private boolean finished;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.pacbio.secondary.analysis.referenceUploader.io.ByteObserver#finish()
	 */
	@Override
	public abstract void finish();
	
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.pacbio.secondary.analysis.referenceUploader.io.ByteObserver#finishInError()
	 */
	@Override
	public abstract void finishInError();
	
	/*
	 * (non-Javadoc)
	 * 
	 * @seecom.pacbio.secondary.analysis.referenceUploader.io.ByteObserver#
	 * observableIsFinished()
	 */
	@Override
	public boolean observableIsFinished() {
		return finished;
	}

	/**
	 * Implementing classes must call super.update(...). This guarantees that the finish() method will be called.
	 * 
	 * @see java.util.Observer#update(java.util.Observable, java.lang.Object)
	 */
	@Override
	public void update(Observable o, Object arg) {
		if (arg instanceof ObservableByteBroadcaster.Signal) {
			if (arg == ObservableByteBroadcaster.Signal.Finished) {
				finished = true;
				finish();

			} else if ( arg == ObservableByteBroadcaster.Signal.Error ) {
				finished = true;
				finishInError();
			}
		}

	}

	

}

/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader.io;

import java.util.Observable;

/**
 * Objects that must both listen to bytes and broadcast them to other consumers
 * should extend this class. This functionality allows producers and consumers
 * to be chained together.
 * 
 * @author jmiller
 */
public abstract class ObservableObserver extends ObservableByteBroadcaster
		implements ByteObserver {

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
	 * @seecom.pacbio.secondary.analysis.referenceUploader.io.ByteObserver#
	 * observableIsFinished()
	 */
	@Override
	public boolean observableIsFinished() {
		return finished;
	}

	/**
	 * Implementing classes must call super.update(...). This guarantees that
	 * the finish() method will be called.
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

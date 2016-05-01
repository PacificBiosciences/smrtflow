/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader.io;

import java.util.Observer;

import com.pacbio.secondary.analysis.referenceUploader.io.ObservableByteBroadcaster.Signal;

/**
 * This interface extends the functionality of java.util's Observer. Normally,
 * an Observer entity in the system should not need to implement this, since
 * there is an abstract implementation {@link AbstractByteObserver} that can be
 * extended.
 * 
 * @author jmiller
 */
public interface ByteObserver extends Observer {

	/**
	 * Finished is finished. It may have finished successfull or in error.
	 * 
	 * @return true if the observer has received a {@link Signal#Error} or
	 *         {@link Signal#Finished} from an observable
	 */
	public boolean observableIsFinished();

	/**
	 * Implementers of observer should close any resources.
	 */
	public void finish();

	/**
	 * Implementers of observer should close any resources, but not do anything
	 * else. For example, continued processing may cause another error,
	 * confusing the root cause.
	 */
	public void finishInError();

}

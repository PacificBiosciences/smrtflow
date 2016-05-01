/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader.io;

import java.util.Observable;

/**
 * Adds a little bit of extra functionality to the basic Java Observable.
 * @author jmiller
 */
public abstract class ObservableByteBroadcaster extends Observable {

	public static enum Signal { Finished, Error };

	
}

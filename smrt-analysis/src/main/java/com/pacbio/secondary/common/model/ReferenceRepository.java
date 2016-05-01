package com.pacbio.secondary.common.model;

import java.io.File;
import java.io.IOException;

import com.pacbio.secondary.common.core.AnalysisContext;

/**
 * The system reference repository.
 */
public class ReferenceRepository {
	private static ReferenceRepository instance;
	
	/**
	 * Singleton instance.
	 */
	public static ReferenceRepository getInstance() {
		if (instance == null)
			instance = new ReferenceRepository();
		return instance;
	}
	
	/**
	 * A representation of the reference index.xml file.
	 * @throws IOException
	 */
	public ReferenceIndex getIndex() throws IOException {
		return ReferenceIndex.open();
	}
	
	/**
	 * Reference info for a given reference id.
	 * @param referenceId
	 * @return the reference info, or null if not found
	 * @throws IOException
	 */
	public ReferenceInfo getReferenceInfo(String referenceId) throws IOException {
		ReferenceEntry entry = getIndex().getEntry(referenceId);
		if (entry == null)
			return null;
		
		File dir = new File(AnalysisContext.getInstance().getReferencesRoot(), 
							entry.getDirectory());
		return ReferenceInfo.load(dir);
	}
}

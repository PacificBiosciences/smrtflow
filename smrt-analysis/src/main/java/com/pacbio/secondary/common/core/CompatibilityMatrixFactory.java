/**
 * 
 */
package com.pacbio.secondary.common.core;

import java.io.InputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

/**
 * Factory class for obtaining a {@link CompatibilityMatrix}.
 * 
 * @author jmiller
 */
public class CompatibilityMatrixFactory {

	private CompatibilityMatrixFactory() {
	}

	
	/**
	 * After obtaining the object, it is the responsibility of the caller to close the inputStream.
	 * @param in
	 * @return
	 */
	public static CompatibilityMatrix open(InputStream in) throws Exception {
		
		if( in == null )
			throw new IllegalArgumentException( "InputStream to CompatibilityMatrix XML cannot be null");
		
		JAXBContext context = JAXBContext.newInstance(CompatibilityMatrix.class);
		Unmarshaller u = context.createUnmarshaller();
		CompatibilityMatrix m = (CompatibilityMatrix) u.unmarshal(in);
		return m;
	}

}

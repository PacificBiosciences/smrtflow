package com.pacbio.secondary.common.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The SMRT Analysis config xml file, read-only.
 */
@XmlRootElement(name = "smrtSecondaryConfig")
public class AnalysisConfig extends AbstractAnalysisConfig {

	/**
	 * Get the config settings, read-only. 
	 * @return read-only instance
	 * @throws IOException  if parsing failed
	 */
	public static AnalysisConfig open() throws IOException {
		AnalysisConfig retval;
		String xml;
		FileInputStream stream = new FileInputStream(new File(
				getAnalysisRoot(), CONFIG_FILE.getPath()));
		try {
			FileChannel fc = stream.getChannel();
			MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0,
					fc.size());
			xml = Charset.defaultCharset().decode(bb).toString();
		} finally {
			stream.close();
		}

		// Remove UTF-8 BOM
		if (!xml.startsWith("<"))
			xml = xml.substring(xml.indexOf("<?xml"));

		try {
			JAXBContext context = JAXBContext.newInstance(AnalysisConfig.class);
			Unmarshaller u = context.createUnmarshaller();
			retval = (AnalysisConfig) u.unmarshal(new StringReader(xml));
		} catch (JAXBException e) {
			throw new IOException("Failed to parse xml", e);
		}
		return retval;
	}
}

/**
 * 
 */
package com.pacbio.secondary.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Utility class for extracting attributes from the movie metadata xml file.
 * 
 * @author jmiller
 */
public class MetadataXml {

	private Document document;

	/**
	 * Construct with a metadata file
	 * 
	 * @param xml
	 * @throws IOException
	 */
	public MetadataXml(File xml) throws IOException {
		InputStream in = null;
		try {
			in = new FileInputStream(xml);
			document = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder().parse(in);
		} catch (Exception e) {
			throw new IOException(e);
		} finally {
			try {
				in.close();
			} catch (Exception e) {
			}
		}

	}

	/**
	 * Construct with metadata path
	 * 
	 * @param path
	 * @throws IOException
	 */
	public MetadataXml(String path) throws IOException {
		this(new File(path));
	}

	/**
	 * Get the content of the ResultsFolder element. Null if it does not exist.
	 * 
	 * @return ResultsFolder
	 */
	public String getResultFolder() {
		return getSingleNode("ResultsFolder").getTextContent();
	}

	public String getCollectionPathUri() {
		return getSingleNode("CollectionPathUri").getTextContent();
	}

	private Node getSingleNode(String element) {
		NodeList nl = document.getElementsByTagName(element);
		if (nl == null || nl.getLength() == 0)
			return null;
		return nl.item(0);
	}

}

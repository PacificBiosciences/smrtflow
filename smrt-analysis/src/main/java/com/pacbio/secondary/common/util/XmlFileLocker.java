package com.pacbio.secondary.common.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

/**
 * Utility class to support simple file locking of xml files,
 * like the system-wide config.xml and the reference index.xml.
 * Using a type parameter other than a Document will use JAXB  
 * to serialize and deserialize.
 * 
 * <p>
 * Example:
 * <pre>{@code
 * XmlFileLocker<Document> config = new XmlFileLocker<Document>();
 * Document doc = config.load(SOME_FILE, true); // get locked instance for editing
 * // ... edit
 * 
 * try {
 *     config.save(doc);
 * } catch(Exception e) {
 *     // ... handle error
 * } finally {
 *     config.unlock();
 * }
 * }</pre>
 * </p>
 */
public class XmlFileLocker<T> {
	private final static Logger LOGGER = Logger.getLogger(XmlFileLocker.class.getName());

	protected Class<T> objectType;
	protected Class<?>[] extraTypes; // for JAXB
	protected FileChannel channel;
	protected FileLock fileLock;	
	
	/**
	 * Non-public constructor.
	 * @param objectType parse xml into this type
	 */
	public XmlFileLocker(Class<T> objectType) {
		this.objectType = objectType;
		this.extraTypes = new Class<?>[]{objectType};
	}
	
	/**
	 * Non-public constructor.
	 * @param jaxbTypes for JAXB deserialization
	 */
	@SuppressWarnings("unchecked")
	public XmlFileLocker(Class<?>... jaxbTypes) {
		if (jaxbTypes == null || jaxbTypes.length == 0)
			throw new IllegalArgumentException("Missing class(es) for JAXB");
		
		this.objectType = (Class<T>)jaxbTypes[0];
		this.extraTypes = jaxbTypes;
	}

	/**
	 * Load the config file.
	 * @param isEditing
	 * @param path
	 * @return the object read from the file
	 * @throws IOException
	 */
	public T load(File path, boolean isEditing)
	throws IOException, OverlappingFileLockException {
		if (!path.exists())
			throw new FileNotFoundException("Cannot load file: " + path);
		
		T retval = null;	
		
		channel = new RandomAccessFile(path, isEditing ? "rw" : "r").getChannel();

		LOGGER.fine("Load xml config from: " + path);		

		if (isEditing)
			lock(0);	
		
		if (path.length() == 0) {
			try {
				retval = objectType.newInstance();
			} catch (Exception e) {
				throw new IOException("Failed to create new object of type " + objectType, e);
			}
			if (!isEditing)
				channel.close();
		} else {
			try {	
				String xml;
				try {
				    ByteBuffer bb = channel.map(MapMode.READ_ONLY, 0, channel.size());			    
				    xml = Charset.defaultCharset().decode(bb).toString();
				    bb = null;	
				    System.gc(); // try to make sure the memory map gets released
				}
				finally {
					if (!isEditing)
						channel.close();				
				}			
				retval = read(xml); 
				
			} catch (Exception e) {
				unlock();
				throw new IOException("Failed to read settings file: " + path, e);
			}
		} 
		LOGGER.fine("Xml config file loaded");
		return retval;
	}
	
	/**
	 * Parse as a DOM or using JAXB
	 * @param xml
	 * @return the target type
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	protected T read(String xml) throws Exception {
		T retval;
		
		if (objectType.equals(Document.class)) {
			retval = (T)DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
				new ByteArrayInputStream(xml.getBytes()));
			
		} else {
			// Remove UTF-8 BOM
			if (!xml.startsWith("<"))
				xml = xml.substring(xml.indexOf("<?xml")); 
			
			try {
				JAXBContext context = JAXBContext.newInstance(extraTypes);
			 	Unmarshaller u = context.createUnmarshaller();
			 	retval = (T) u.unmarshal(new StringReader(xml));			
			} catch (JAXBException e) {
				throw new IOException("Failed to parse xml", e);
			}
		}
		return retval;
	}
	
	/**
	 * Prepare for writing with Java nio.
	 * @param object
	 * @return xml contents as a char sequence
	 * @throws IOException 
	 */
	protected CharSequence write(T object) throws IOException {
		StringWriter writer = new StringWriter();
		
		// Write DOM
		if (objectType.equals(Document.class)) {
			Transformer serializer;
			try {
				serializer = TransformerFactory.newInstance().newTransformer();
				serializer.setOutputProperty(OutputKeys.ENCODING,"ISO-8859-1");
				serializer.setOutputProperty(OutputKeys.INDENT,"yes");
				serializer.transform(new DOMSource((Document)object), new StreamResult(writer));
				
			} catch (Exception e) {
				throw new IOException("Failed to save xml config file", e);
			}		
			
		// Write JAXB
		} else {
			try {
				JAXBContext context = JAXBContext.newInstance(extraTypes);
				Marshaller m = context.createMarshaller();
				m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
				m.marshal(object, writer);
				
			} catch (JAXBException e) {
				throw new IOException("Failed writing xml with JAXB", e);
			}
		}
		writer.close();
		return writer.getBuffer();
	}
	
	/**
	 * Lock the file for editing.
	 * @throws IOException
	 */
	protected void lock(int attempt) throws IOException {
		final int maxTries = 3;	
		final int waitTime = 20;
		
		if (attempt >= maxTries) {
			LOGGER.log(Level.WARNING, "Failed to lock file after " + maxTries + " attempts.");
			throw new OverlappingFileLockException();
		}
			
		try {			
			fileLock = channel.tryLock();
			
		} catch (OverlappingFileLockException e) {
			LOGGER.log(Level.WARNING, 
					"Xml file is already locked on attempt " 
					+ attempt + " of " + maxTries);
			
			synchronized(channel) {
				try {
					channel.wait(waitTime);
				} catch (InterruptedException ie) {
					LOGGER.log(Level.WARNING, "Thread interrupted", ie);
				}
			}
			lock(++attempt);
		}
		
		if (fileLock == null)
			throw new IOException("Failed to get lock on analysis settings file.");
	}
	
	/**
	 * Release the file lock, if any.
	 */
	public void unlock() {
		try {
			if (fileLock != null && fileLock.isValid()) {
				fileLock.release();
				fileLock = null;
			}
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Failed to release file lock", e);
		}
	}
	
	/**
	 * Save changes and release the file lock.
	 * @throws IOException if saving failed, in which case the file will not be unlocked
	 */
	public void save(T object) throws IOException {
		if (fileLock == null || !fileLock.isValid())
			throw new IOException(
					"Cannot save xml file because the file lock is null or invalid.");
			
		CharBuffer cb = CharBuffer.wrap(write(object));
		ByteBuffer bb = Charset.defaultCharset().encode(cb);
		channel.write(bb);				
		channel.truncate(bb.limit());
		channel.close();
				
		unlock();
		
		LOGGER.fine("Saved xml config file.");
	}
	
	/**
	 * Make sure file is unlocked.
	 */
	public void finalize() {
		unlock();
	}
}

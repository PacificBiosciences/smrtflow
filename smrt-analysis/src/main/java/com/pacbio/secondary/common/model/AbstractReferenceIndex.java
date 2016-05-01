/**
 * 
 */
package com.pacbio.secondary.common.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author jmiller
 *
 */

public abstract class AbstractReferenceIndex {
	protected final static Logger LOGGER = Logger.getLogger(AbstractReferenceIndex.class
			.getName());
	
	protected Properties properties;
	protected List<ReferenceEntry> references;
	
	protected File indexXml;
	
	
	
	synchronized public void addEntry(ReferenceEntry re) {
		
		if( re.getId() == null )
			throw new IllegalStateException( "id cannot be null!");
		
		if( getEntry(re.getId()) != null )
			throw new IllegalStateException( re.getId() + " already exists!");
		
		if (re.getLastModified() == null)
			re.setLastModified(new Date());
		getReferences().add(re);
	}

	/**
	 * Deletes an xml entry, but does not delete the reference dir. The caller
	 * is responsible for that.
	 * 
	 * Does NOT save the document. Caller must save().
	 * 
	 * @param id
	 * @throws Exception 
	 */
	public void deleteEntry(String id) throws Exception {
		LOGGER.info("Delete reference " + id);
		ReferenceEntry re = getEntry(id);
		if (re == null)
			throw new IllegalArgumentException("Reference id " + id
					+ " not found");
		getReferences().remove(getReferences().indexOf(re));
//		save();
//		LOGGER.info("Reference " + id + " removed from index file");
	}
	
	
	/**
	 * Get the parent dir of index.xml, which == the repository.
	 * 
	 * @return File the reference repository
	 */
	public File getReferenceRepos() {
		return new File(this.indexXml.getParent());
	}
	

	public void setIndexXml(File indexXml) {
		this.indexXml = indexXml;
	}
	
	
	public abstract void save() throws Exception;
	
	
	public abstract void unlock() throws IOException;

	/**
	 * The properties element in the reference index.xml file.
	 */
	public static class Properties {
		private String title;
		private Date lastModified;
		private String system;
		private String user;

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		@XmlElement(name = "last_modified")
		public Date getLastModified() {
			return lastModified;
		}

		public void setLastModified(Date lastModified) {
			this.lastModified = lastModified;
		}

		public String getSystem() {
			return system;
		}

		public void setSystem(String system) {
			this.system = system;
		}

		public String getUser() {
			return user;
		}

		public void setUser(String user) {
			this.user = user;
		}
	}

	
	
	/**
	 * Lookup reference entries by name, case sensitive.
	 * 
	 * @param name
	 * @return non-null list of reference entries
	 */
	public List<ReferenceEntry> getEntries(String name) {
		return getEntries(name, false);
	}

	/**
	 * Lookup reference entries by name.
	 * 
	 * @param name
	 * @param ignoreCase
	 *            specifies how to match the name
	 * @return non-null list of reference entries
	 */
	public List<ReferenceEntry> getEntries(String name, boolean ignoreCase) {
		List<ReferenceEntry> list = new ArrayList<ReferenceEntry>();
		if (references != null) {
			for (ReferenceEntry re : references) {
				if ((ignoreCase && name.equalsIgnoreCase(re.getName()))
						|| (!ignoreCase && name.equals(re.getName())))
					list.add(re);
			}
		}
		return list;
	}

	
	public Properties getProperties() {
		if (properties == null)
			properties = new Properties();
		return properties;
	}

	public void setProperties(Properties properties) {
		this.properties = properties;
	}

	@XmlElement(name = "reference")
	public List<ReferenceEntry> getReferences() {
		if (references == null)
			references = new ArrayList<ReferenceEntry>();
		return references;
	}

	/**
	 * Lookup reference entry by id.
	 * 
	 * @param id
	 * @return reference entry or null if not found
	 */
	public ReferenceEntry getEntry(String id) {
		if (references != null) {
			for (ReferenceEntry re : references) {
				if (id.equals(re.getId()))
					return re;
			}
		}
		return null;
	}
	
	
	/**
	 * Save an index which does not yet exist in the repository.
	 * 
	 * @throws IOException
	 */
	protected Marshaller getMarshaller() throws IOException {
		try {
			JAXBContext context = JAXBContext.newInstance(getClass(),
					Properties.class, ReferenceEntry.class);
			Marshaller m = context.createMarshaller();
			m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			return m;
			
//			marshal(m);
			
//			m.marshal(this, this.indexXml);
		} catch (JAXBException e) {
			throw new IOException("Failed to save new file: "
					+ indexXml.getAbsolutePath(), e);
		}
	}

	protected abstract void marshal(Marshaller m) throws Exception;
	
}

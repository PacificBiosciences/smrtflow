package com.pacbio.secondary.common.model;

import java.io.File;
import java.io.IOException;
import java.nio.channels.OverlappingFileLockException;
import java.util.Date;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlRootElement;

import com.pacbio.secondary.common.core.AnalysisContext;
import com.pacbio.secondary.common.util.XmlFileLocker;

/**
 * Representation of the reference index xml file.
 */
@XmlRootElement(name = "reference_index")
public class ReferenceIndex extends AbstractReferenceIndex {
//	private final static Logger LOGGER = Logger.getLogger(ReferenceIndex.class
//			.getName());
//	private Properties properties;
//	private List<ReferenceEntry> references;

	XmlFileLocker<ReferenceIndex> manager;
//	private File indexXml;

	/**
	 * Get the reference index, read-only. Assumes reference repository has been
	 * defined in config.xml.
	 * 
	 * @return read-only instance
	 * @throws IOException
	 *             if parsing failed
	 */
	public static ReferenceIndex open() throws IOException {
		return open(false, getConfigReferencesRoot());
	}

	/**
	 * Get the reference index, read-only. Uses the supplied reference
	 * repository.
	 * 
	 * @param referenceRepository
	 * @return
	 * @throws IOException
	 */
	public static ReferenceIndex open(String referenceRepository)
			throws IOException {
		return open(false, new File(referenceRepository));
	}

	/**
	 * Lock the reference index and get an editable instance. Assumes reference
	 * repository has been defined in config.xml.
	 * 
	 * @throws IOException
	 *             if parsing failed
	 * @throws OverlappingFileLockException
	 *             if file is already locked
	 */
	public static ReferenceIndex openForEdit() throws IOException {
		return open(true, getConfigReferencesRoot());
	}

	/**
	 * Lock the reference index found in the supplied repository dir and get an
	 * editable instance.
	 * 
	 * @param referenceRepository
	 * @return
	 * @throws IOException
	 */
	public static ReferenceIndex openForEdit(String referenceRepository)
			throws IOException {
		return open(true, new File(referenceRepository));
	}

	/**
	 * Open or open for editing.
	 * 
	 * @param isEditing
	 * @param referenceRepository
	 * @return
	 * @throws IOException
	 */
	protected static ReferenceIndex open(boolean isEditing,
			File referenceRepository) throws IOException {
		XmlFileLocker<ReferenceIndex> manager = new XmlFileLocker<ReferenceIndex>(
				ReferenceIndex.class, Properties.class, ReferenceEntry.class);

		ReferenceIndex index = null;

		File indexXml = new File(referenceRepository, "index.xml");
		if (!indexXml.exists()) {
			index = new ReferenceIndex();
		} else {
			index = manager.load(indexXml, isEditing);
			if (isEditing)
				index.manager = manager;
		}

		index.setIndexXml(indexXml);

		return index;
	}
	
//	/**
//	 * Get the parent dir of index.xml, which == the repository.
//	 * 
//	 * @return File the reference repository
//	 */
//	public File getReferenceRepos() {
//		return new File(this.indexXml.getParent());
//	}
//	
//
//	private void setIndexXml(File indexXml) {
//		this.indexXml = indexXml;
//	}

	/**
	 * Get the reference root from config.xml. Within the secondary system, this
	 * is the normal way to get the referenceRoot. However, if an other
	 * reference root is specified at load time, {@link #openForEdit(String)},
	 * then that reference root is used.
	 * 
	 * @return absolute path.
	 * @throws IOException
	 */
	protected static File getConfigReferencesRoot() throws IOException {
		return AnalysisContext.getInstance().getReferencesRoot();
	}

	/**
	 * Save changes.
	 * @throws Exception 
	 */
	public void save() throws Exception {
		this.getProperties().setLastModified(new Date());
		if (manager != null) {
			manager.save(this);
			manager = null;
		} else {
			Marshaller m = getMarshaller();
			marshal(m);
		}
	}

	

	/**
	 * Release the file.
	 * 
	 * @throws IOException
	 */
	public void unlock() throws IOException {
		if (manager != null) {
			manager.unlock();
			manager = null;
		}
	}

	@Override
	protected void marshal(Marshaller m) throws Exception {
		m.marshal(this, this.indexXml);
	}

//	public Properties getProperties() {
//		if (properties == null)
//			properties = new Properties();
//		return properties;
//	}
//
//	public void setProperties(Properties properties) {
//		this.properties = properties;
//	}
//
//	@XmlElement(name = "reference")
//	public List<ReferenceEntry> getReferences() {
//		if (references == null)
//			references = new ArrayList<ReferenceEntry>();
//		return references;
//	}
//
//	/**
//	 * Lookup reference entry by id.
//	 * 
//	 * @param id
//	 * @return reference entry or null if not found
//	 */
//	public ReferenceEntry getEntry(String id) {
//		if (references != null) {
//			for (ReferenceEntry re : references) {
//				if (id.equals(re.getId()))
//					return re;
//			}
//		}
//		return null;
//	}

//	/**
//	 * Lookup reference entries by name, case sensitive.
//	 * 
//	 * @param name
//	 * @return non-null list of reference entries
//	 */
//	public List<ReferenceEntry> getEntries(String name) {
//		return getEntries(name, false);
//	}
//
//	/**
//	 * Lookup reference entries by name.
//	 * 
//	 * @param name
//	 * @param ignoreCase
//	 *            specifies how to match the name
//	 * @return non-null list of reference entries
//	 */
//	public List<ReferenceEntry> getEntries(String name, boolean ignoreCase) {
//		List<ReferenceEntry> list = new ArrayList<ReferenceEntry>();
//		if (references != null) {
//			for (ReferenceEntry re : references) {
//				if ((ignoreCase && name.equalsIgnoreCase(re.getName()))
//						|| (!ignoreCase && name.equals(re.getName())))
//					list.add(re);
//			}
//		}
//		return list;
//	}

//	synchronized public void addEntry(ReferenceEntry re) {
//		if (re.getLastModified() == null)
//			re.setLastModified(new Date());
//		getReferences().add(re);
//	}
//
//	/**
//	 * Deletes an xml entry, but does not delete the reference dir. The caller
//	 * is reposible for that.
//	 * 
//	 * @param id
//	 * @throws IOException
//	 *             if saving failed or file deletion failed
//	 */
//	public void deleteEntry(String id) throws IOException {
//		LOGGER.info("Delete reference " + id);
//		ReferenceEntry re = getEntry(id);
//		if (re == null)
//			throw new IllegalArgumentException("Reference id " + id
//					+ " not found");
//		getReferences().remove(getReferences().indexOf(re));
//		save();
//		LOGGER.info("Reference " + id + " removed from index file");
//	}
//
//	/**
//	 * The properties element in the reference index.xml file.
//	 */
//	public static class Properties {
//		private String title;
//		private Date lastModified;
//		private String system;
//		private String user;
//
//		public String getTitle() {
//			return title;
//		}
//
//		public void setTitle(String title) {
//			this.title = title;
//		}
//
//		@XmlElement(name = "last_modified")
//		public Date getLastModified() {
//			return lastModified;
//		}
//
//		public void setLastModified(Date lastModified) {
//			this.lastModified = lastModified;
//		}
//
//		public String getSystem() {
//			return system;
//		}
//
//		public void setSystem(String system) {
//			this.system = system;
//		}
//
//		public String getUser() {
//			return user;
//		}
//
//		public void setUser(String user) {
//			this.user = user;
//		}
//	}
//
//	/**
//	 * Get the parent dir of index.xml, which == the repository.
//	 * 
//	 * @return File the reference repository
//	 */
//	public File getReferenceRepos() {
//		return new File(this.indexXml.getParent());
//	}
}

package com.pacbio.secondary.common.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.pacbio.secondary.common.model.Reference.Organism;
import com.pacbio.secondary.common.util.Iso8601Date;

/**
 * Representation of a reference.index.xml file.
 */
@XmlType(propOrder={"organism", "reference", "contigs"})
@XmlRootElement(name = "reference_info")
public class ReferenceInfo {
	public final static String FILE_NAME = "reference.info.xml";
	
	private String id;
	private String version;
	private Date lastModified;
	private Organism organism;
	private Reference reference;
	private List<Contig> contigs = new ArrayList<Contig>();


	/**
	 * Get the reference info.
	 * 
	 * @return reference info
	 * @throws IOException
	 *             if parsing failed
	 */
	public static ReferenceInfo load(File dir) throws IOException {
		ReferenceInfo info;
		try {
			JAXBContext context = JAXBContext.newInstance(
					ReferenceInfo.class, Reference.class, Reference.File.class, 
					Reference.IndexFile.class, Contig.class, Digest.class);
		 	Unmarshaller u = context.createUnmarshaller();
		 	info = (ReferenceInfo)u.unmarshal(new File(dir, FILE_NAME));
		} catch (JAXBException e) {
			throw new IOException("Failed to load reference info from file: " + dir, e);
		}
		return info;
	}
	
	/**
	 * Save the reference info file to the specified directory.
	 * @param dir
	 * @throws IOException
	 */
	public void save(File dir) throws IOException {
		try {
			JAXBContext context = JAXBContext.newInstance(
					ReferenceInfo.class, Reference.class, Reference.File.class, 
					Reference.IndexFile.class, Contig.class, Digest.class);
		 	Marshaller u = context.createMarshaller();
		 	setLastModified( new Date() );
		 	u.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
		 	u.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "http://www.w3.org/2001/XMLSchema-instance");
		 	u.marshal(this, new File(dir, FILE_NAME));
		} catch (JAXBException e) {
			throw new IOException("Failed to save reference info to file: " + dir, e);
		}		
	}
	
	@XmlAttribute
	public String getId() {
		return id;
	}
	
	public void setId(String id) {
		this.id = id;
	}
	
	@XmlAttribute
	public String getVersion() {
		return version;
	}
	
	public void setVersion(String version) {
		this.version = version;
	}
	
	@XmlJavaTypeAdapter(Iso8601Date.class)
	@XmlAttribute(name = "last_modified")	
	public Date getLastModified() {
		return lastModified;
	}
	
	public void setLastModified(Date lastModified) {
		this.lastModified = lastModified;
	}

	public Reference getReference() {
		return reference;
	}

	public void setReference(Reference reference) {
		this.reference = reference;
	}

	@XmlElementWrapper(name = "contigs")
	@XmlElement(name = "contig")
	public List<Contig> getContigs() {
		return contigs;
	}

	public Organism getOrganism() {
		return organism ;
	}
	
	public void setOrganism(Organism organism) {
		this.organism = organism;
	}

}

package com.pacbio.secondary.common.model;

import java.util.Date;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.pacbio.secondary.common.util.Iso8601Date;

/**
 * An entry in the reference index file.
 */
@XmlRootElement
public class ReferenceEntry {

	public static enum State {
		Active {
			@Override
			public String toString() {
				return "active";
			}
		},
		Failed {
			@Override
			public String toString() {
				return "failed";
			}
		},
		Inactive {
			@Override
			public String toString() {
				return "inactive";
			}
		},
		Processing {
			@Override
			public String toString() {
				return "processing";
			}
		}
	}

	public static enum Type {
		Sample {
			@Override
			public String toString() {
				return "sample";
			}
		},
		Control {
			@Override
			public String toString() {
				return "control";
			}
		};
	}

	private String name;
	private String version;
	private String id;
	private String directory;
	private String metadata;
	private Date lastModified;
	private String organism;
	private String user;
	private String state;
	private String type;
	private Digest digest;
	private String jobId;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getDirectory() {
		return directory;
	}

	public void setDirectory(String directory) {
		this.directory = directory;
	}

	public String getMetadata() {
		return metadata;
	}

	public void setMetadata(String metadata) {
		this.metadata = metadata;
	}

	@XmlJavaTypeAdapter(Iso8601Date.class)
	@XmlElement(name = "last_modified")
	public Date getLastModified() {
		return lastModified;
	}

	public void setLastModified(Date lastModified) {
		this.lastModified = lastModified;
	}

	public String getOrganism() {
		return organism;
	}

	/**
	 * Use {@link ReferenceInfo#setOrganism(com.pacbio.secondary.common.model.Reference.Organism)
	 * @param organism
	 */
	@Deprecated
	public void setOrganism(String organism) {
		this.organism = organism;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getState() {
		return state;
	}

	@XmlTransient
	public void setState(State state) {
		setState(state.toString());
	}

	/**
	 * Use {@link #setState(State)}
	 * 
	 * @param state
	 */
	// annoying that jaxb won't serialize unless this is public.
	public void setState(String state) {
		if( state == null)
			return;
		// Instead, illegal arg check...
		for (State s : State.values()) {
			if (s.toString().equals(state)) {
				this.state = state;
				return;
			}
		}
		throw new IllegalArgumentException("No enum for " + state);
	}

	public String getType() {
		return type;
	}

	/**
	 * Use {@link #setType(Type)}
	 * 
	 * @param type
	 */
	public void setType(String type) {
		if( type == null )
			return;
		// Instead, illegal arg check...
		for (Type t : Type.values()) {
			if (t.toString().equals(type)) {
				this.type = type;
				return;
			}
		}
		throw new IllegalArgumentException("No enum for " + type);
	}

	@XmlTransient
	public void setType(Type type) {
		setType(type.toString());
	}

	public Digest getDigest() {
		return digest;
	}
	
	public void setDigest( Digest digest ) {
		this.digest = digest;
	}

	public String getJobId() {
		return jobId;
	}
	
	public void setJobId(String jobId) {
		this.jobId = jobId;
	}
	
}

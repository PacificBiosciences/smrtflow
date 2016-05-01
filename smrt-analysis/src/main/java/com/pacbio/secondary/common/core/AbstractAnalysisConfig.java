/**
 * 
 */
package com.pacbio.secondary.common.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/**
 * @author jmiller
 *
 */
@XmlType(propOrder = {"system", "components"})
public abstract class AbstractAnalysisConfig {
	
	
	protected static Logger LOGGER = Logger.getLogger(AbstractAnalysisConfig.class
			.getName());

	/** Path to the smrtanalysis temp directory. */
	public static final String TEMP_DIR = "temp.dir";
	/** Path to the smrtanalysis job directory. */
	public static final String JOB_DIR = "job.root.dir";
	/** Path to the smrtanalysis protocol directory. */
	public static final String PROTOCOL_DIR = "protocol.dir";
	/** Path where smrtanalysis applications can put their log files. */
	public static final String LOG_DIR = "log.dir";
	/** Path to the reference repository. */
	public static final String REFERENCE_DIR = "reference.dir";
	public static final File CONFIG_FILE = new File("etc", "config.xml");
	private static final File DEFAULT_HOME = new File(new File(File.separator,
				"opt"), "smrtanalysis");

	private static File analysisRoot;
	private System system;
	private Components components;

	/**
	 * Root directory of secondary analysis system.
	 * Should always return the real (no symlinks) path.
	 * @return value of environment variable or default
	 * @throws IOException 
	 */
	public static File getAnalysisRoot() {
		if (analysisRoot == null) {
			String home = java.lang.System.getenv("SEYMOUR_HOME");
			try {
				analysisRoot = home == null ? DEFAULT_HOME : new File(home).getCanonicalFile();
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, "Cannot resolve real path for env SEYMOUR_HOME: " + home);
				throw new RuntimeException(e);
			}
		}
		
		return analysisRoot;
	}

	/**
	 * Set the root directory of the secondary analysis system.
	 * Applies to current Java instance only; the setting will revert
	 * when the VM exits.
	 * @param root
	 * @throws FileNotFoundException
	 */
	public static void setAnalysisRoot(File root)
			throws FileNotFoundException {
				if (root != null && !root.exists())
					throw new FileNotFoundException("Path does not exist: " + root);
				analysisRoot = root;
			}

	public void setSystem(System system) {
		this.system = system;
	}

	public System getSystem() {
		return system;
	}

	public static class System {
		private Host host;
		private List<Setting> settings;
		private List<MultiSetting> multiSettings;
		private List<Path> paths;
	
		public Host getHost() {
			return host;
		}
	
		public void setHost(Host host) {
			this.host = host;
		}
	
		@XmlElement(name = "setting")
		public List<Setting> getSettings() {
			if (settings == null)
				settings = new ArrayList<Setting>();
			return settings;
		}
		
		public Setting getSetting(String name) {
			for (Setting s : getSettings())
				if (s.getName().equals(name))
					return s;
			return null;
		}
	
		@XmlElement(name = "multiSetting")
		public List<MultiSetting> getMultiSettings() {
			if (multiSettings == null)
				multiSettings = new ArrayList<MultiSetting>();
			return multiSettings;
		}
		
		public MultiSetting getMultiSetting(String name) {
			for (MultiSetting ms : getMultiSettings())
				if (ms.getName().equals(name))
					return ms;
			return null;
		}
		
		@XmlElement(name = "path")
		public List<Path> getPaths() {
			if (paths == null)
				paths = new ArrayList<Path>();
			return paths;
		}
		
		public Path getPath(String name) {
			for (Path p : getPaths())
				if (p.getName().equals(name))
					return p;
			return null;
		}
		
		public static class Host {
			private String name;
			private String httpport;
			private String httpsport;
	
			public void setName(String name) {
				this.name = name;
			}
	
			@XmlAttribute
			public String getName() {
				return name;
			}
	
			public void setHttpport(String httpport) {
				this.httpport = httpport;
			}
			
			@XmlAttribute
			public String getHttpport() {
				return httpport;
			}
	
			public void setHttpsport(String httpsport) {
				this.httpsport = httpsport;
			}
			
			@XmlAttribute
			public String getHttpsport() {
				return httpsport;
			}			
		}
	
		public static class Setting {
			private String name;
			private String value;
	
			public void setName(String name) {
				this.name = name;
			}
	
			@XmlAttribute
			public String getName() {
				return name;
			}
	
			public void setValue(String value) {
				this.value = value;
			}
	
			@XmlAttribute
			public String getValue() {
				return value;
			}
		}
	
		public static class MultiSetting {
			private String name;
			private List<String> values;
	
			@XmlElement(name = "value")
			public List<String> getValues() {
				if (values == null)
					values = new ArrayList<String>();
				return values;
			}
	
			public void setName(String name) {
				this.name = name;
			}
	
			@XmlAttribute
			public String getName() {
				return name;
			}
		}
	
		public static class Path {
			private String dir;
			private String name;
	
			public void setDir(String dir) {
				this.dir = dir;
			}
	
			@XmlAttribute
			public String getDir() {
				return dir;
			}
	
			public void setName(String name) {
				this.name = name;
			}
	
			@XmlAttribute
			public String getName() {
				return name;
			}
		}
	}

	public Components getComponents() { 
		return components;
	}

	public void setComponents(Components components) {
		this.components = components;
	}

	public static class Components {
		private String build;
		private String version;
		private String prior;
		private List<Component> components;
	
		@XmlAttribute
		public String getVersion() {
			return version;
		}
	
		public void setVersion(String version) {
			this.version = version;
		}
		
		@XmlElement(name="component")
		public List<Component> getComponents() {
			if (components == null)
				components = new ArrayList<Component>();
			return components;
		}
		
		public Component getComponent(String name) {
			for (Component c : getComponents())
				if (c.getName().equals(name))
					return c;
			return null;
		}
	
		public void setBuild(String build) {
			this.build = build;
		}
	
		@XmlAttribute
		public String getBuild() {
			return build;
		}

		public void setPrior(String prior) {
			this.prior = prior;
		}
		
		@XmlAttribute
		public String getPrior() {
			return prior;
		}
	
		public static class Component {
			private String name;
			private String displayName;
			private String build;
			private String version;
			private String dir;
	
			@XmlAttribute
			public String getBuild() {
				return build;
			}
	
			public void setBuild(String build) {
				this.build = build;
			}
	
			@XmlAttribute
			public String getDir() {
				return dir;
			}
	
			public void setDir(String dir) {
				this.dir = dir;
			}
	
			@XmlAttribute
			public String getName() {
				return name;
			}
	
			public void setName(String name) {
				this.name = name;
			}
	
			@XmlAttribute
			public String getVersion() {
				return version;
			}
	
			public void setVersion(String version) {
				this.version = version;
			}
	
			public void setDisplayName(String displayName) {
				this.displayName = displayName;
			}
	
			@XmlAttribute
			public String getDisplayName() {
				return displayName;
			}
		}

		
	}

	public String toString() {
		StringWriter writer = new StringWriter();
		try {
			JAXBContext context = JAXBContext.newInstance(AnalysisConfig.class);
			Marshaller m = context.createMarshaller();
			m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
		 	m.marshal(this, writer);
		} catch (JAXBException e) {
			throw new RuntimeException("Failed to write config.xml");
		}
		return writer.toString();
	}

}

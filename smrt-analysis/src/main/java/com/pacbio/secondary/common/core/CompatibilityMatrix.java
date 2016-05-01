/**
 * 
 */
package com.pacbio.secondary.common.core;

import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

import com.pacbio.secondary.common.core.CompatibilityMatrix.Permissible;

/**
 * Model object for XML compatibility matrix,
 * 
 * @author jmiller
 */
@XmlRootElement(name = "runtime")
public class CompatibilityMatrix {

	private List<ColumnInput> columnInputs;
//	private String version;
	private List<Permissible> permissibles;

//	@XmlAttribute(name = "version")
//	public String getRuntimeVersion() {
//		return version;
//	}
//
//	public void setRuntimeVersion(String version) {
//		this.version = version;
//	}

	@XmlElement(name = "columnInput")
	public List<ColumnInput> getColumnInputs() {
		return columnInputs;
	}

	public void setColumnInputs(List<ColumnInput> columnInputs) {
		this.columnInputs = columnInputs;
	}

	@XmlElementWrapper(name = "permissibles" )
	@XmlElement(name = "permissible")
	public List<Permissible> getPermissibles() {
		return permissibles;
	}
	
	public void setPermissibles(List<Permissible> permissibles) {
		this.permissibles = permissibles;
	}


	public static class ColumnInput {

		private String version;
		private List<RowInput> rowInputs;

		@XmlAttribute
		public String getVersion() {
			return version;
		}

		public void setVersion(String version) {
			this.version = version;
		}

		@XmlElement(name = "rowInput")
		public List<RowInput> getRowInputs() {
			return this.rowInputs;
		}

		public void setRowInputs(List<RowInput> rowInputs) {
			this.rowInputs = rowInputs;
		}

	}

	public static class RowInput {

		private String version;
		private List<Permissible> permissibles;

		@XmlAttribute
		public String getVersion() {
			return version;
		}

		public void setVersion(String version) {
			this.version = version;
		}

		public void setPermissibles(List<Permissible> permissibles) {
			this.permissibles = permissibles;
		}

		@XmlElement(name = "permissible")
		public List<Permissible> getPermissibles() {
			return permissibles;
		}

	}

	public static class Permissible {

		private Boolean value;
		private Message message;
		private String id;
		private String useId;

		@XmlAttribute
		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		@XmlAttribute
		public String getUseId() {
			return useId;
		}

		public void setUseId(String useId) {
			this.useId = useId;
		}

		@XmlAttribute
		public Boolean getValue() {
			return value;
		}

		public void setValue(Boolean value) {
			this.value = value;
		}

		public void setMessage(Message message) {
			this.message = message;
		}

		@XmlElement(name = "message")
		public Message getMessage() {
			return message;
		}

	}

	public static class Message {

		private String level;
		private String content;

		@XmlAttribute
		public String getLevel() {
			return level;
		}

		public void setLevel(String level) {
			this.level = level;
		}

		public void setContent(String content) {
			this.content = content;
		}

		@XmlValue()
		public String getContent() {
			if (content != null)
				return content.trim();
			return content;
		}

	}

}

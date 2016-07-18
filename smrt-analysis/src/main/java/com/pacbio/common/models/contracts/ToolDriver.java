/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package com.pacbio.common.models.contracts;

import org.apache.avro.specific.SpecificData;

@SuppressWarnings("all")
@org.apache.avro.specific.AvroGenerated
public class ToolDriver extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = 6970026867017052219L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"ToolDriver\",\"namespace\":\"com.pacbio.common.models.contracts\",\"fields\":[{\"name\":\"exe\",\"type\":\"string\",\"doc\":\"path to exe. The first arg will the the resolved tool contract JSON\"},{\"name\":\"serialization\",\"type\":\"string\",\"doc\":\"Serialization type. Either 'json' or 'avro' binary format \"}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }
  /** path to exe. The first arg will the the resolved tool contract JSON */
  @Deprecated public java.lang.CharSequence exe;
  /** Serialization type. Either 'json' or 'avro' binary format  */
  @Deprecated public java.lang.CharSequence serialization;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public ToolDriver() {}

  /**
   * All-args constructor.
   * @param exe path to exe. The first arg will the the resolved tool contract JSON
   * @param serialization Serialization type. Either 'json' or 'avro' binary format 
   */
  public ToolDriver(java.lang.CharSequence exe, java.lang.CharSequence serialization) {
    this.exe = exe;
    this.serialization = serialization;
  }

  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return exe;
    case 1: return serialization;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: exe = (java.lang.CharSequence)value$; break;
    case 1: serialization = (java.lang.CharSequence)value$; break;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  /**
   * Gets the value of the 'exe' field.
   * @return path to exe. The first arg will the the resolved tool contract JSON
   */
  public java.lang.CharSequence getExe() {
    return exe;
  }

  /**
   * Sets the value of the 'exe' field.
   * path to exe. The first arg will the the resolved tool contract JSON
   * @param value the value to set.
   */
  public void setExe(java.lang.CharSequence value) {
    this.exe = value;
  }

  /**
   * Gets the value of the 'serialization' field.
   * @return Serialization type. Either 'json' or 'avro' binary format 
   */
  public java.lang.CharSequence getSerialization() {
    return serialization;
  }

  /**
   * Sets the value of the 'serialization' field.
   * Serialization type. Either 'json' or 'avro' binary format 
   * @param value the value to set.
   */
  public void setSerialization(java.lang.CharSequence value) {
    this.serialization = value;
  }

  /**
   * Creates a new ToolDriver RecordBuilder.
   * @return A new ToolDriver RecordBuilder
   */
  public static com.pacbio.common.models.contracts.ToolDriver.Builder newBuilder() {
    return new com.pacbio.common.models.contracts.ToolDriver.Builder();
  }

  /**
   * Creates a new ToolDriver RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new ToolDriver RecordBuilder
   */
  public static com.pacbio.common.models.contracts.ToolDriver.Builder newBuilder(com.pacbio.common.models.contracts.ToolDriver.Builder other) {
    return new com.pacbio.common.models.contracts.ToolDriver.Builder(other);
  }

  /**
   * Creates a new ToolDriver RecordBuilder by copying an existing ToolDriver instance.
   * @param other The existing instance to copy.
   * @return A new ToolDriver RecordBuilder
   */
  public static com.pacbio.common.models.contracts.ToolDriver.Builder newBuilder(com.pacbio.common.models.contracts.ToolDriver other) {
    return new com.pacbio.common.models.contracts.ToolDriver.Builder(other);
  }

  /**
   * RecordBuilder for ToolDriver instances.
   */
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<ToolDriver>
    implements org.apache.avro.data.RecordBuilder<ToolDriver> {

    /** path to exe. The first arg will the the resolved tool contract JSON */
    private java.lang.CharSequence exe;
    /** Serialization type. Either 'json' or 'avro' binary format  */
    private java.lang.CharSequence serialization;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(com.pacbio.common.models.contracts.ToolDriver.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.exe)) {
        this.exe = data().deepCopy(fields()[0].schema(), other.exe);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.serialization)) {
        this.serialization = data().deepCopy(fields()[1].schema(), other.serialization);
        fieldSetFlags()[1] = true;
      }
    }

    /**
     * Creates a Builder by copying an existing ToolDriver instance
     * @param other The existing instance to copy.
     */
    private Builder(com.pacbio.common.models.contracts.ToolDriver other) {
            super(SCHEMA$);
      if (isValidValue(fields()[0], other.exe)) {
        this.exe = data().deepCopy(fields()[0].schema(), other.exe);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.serialization)) {
        this.serialization = data().deepCopy(fields()[1].schema(), other.serialization);
        fieldSetFlags()[1] = true;
      }
    }

    /**
      * Gets the value of the 'exe' field.
      * path to exe. The first arg will the the resolved tool contract JSON
      * @return The value.
      */
    public java.lang.CharSequence getExe() {
      return exe;
    }

    /**
      * Sets the value of the 'exe' field.
      * path to exe. The first arg will the the resolved tool contract JSON
      * @param value The value of 'exe'.
      * @return This builder.
      */
    public com.pacbio.common.models.contracts.ToolDriver.Builder setExe(java.lang.CharSequence value) {
      validate(fields()[0], value);
      this.exe = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'exe' field has been set.
      * path to exe. The first arg will the the resolved tool contract JSON
      * @return True if the 'exe' field has been set, false otherwise.
      */
    public boolean hasExe() {
      return fieldSetFlags()[0];
    }


    /**
      * Clears the value of the 'exe' field.
      * path to exe. The first arg will the the resolved tool contract JSON
      * @return This builder.
      */
    public com.pacbio.common.models.contracts.ToolDriver.Builder clearExe() {
      exe = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'serialization' field.
      * Serialization type. Either 'json' or 'avro' binary format 
      * @return The value.
      */
    public java.lang.CharSequence getSerialization() {
      return serialization;
    }

    /**
      * Sets the value of the 'serialization' field.
      * Serialization type. Either 'json' or 'avro' binary format 
      * @param value The value of 'serialization'.
      * @return This builder.
      */
    public com.pacbio.common.models.contracts.ToolDriver.Builder setSerialization(java.lang.CharSequence value) {
      validate(fields()[1], value);
      this.serialization = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'serialization' field has been set.
      * Serialization type. Either 'json' or 'avro' binary format 
      * @return True if the 'serialization' field has been set, false otherwise.
      */
    public boolean hasSerialization() {
      return fieldSetFlags()[1];
    }


    /**
      * Clears the value of the 'serialization' field.
      * Serialization type. Either 'json' or 'avro' binary format 
      * @return This builder.
      */
    public com.pacbio.common.models.contracts.ToolDriver.Builder clearSerialization() {
      serialization = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    @Override
    public ToolDriver build() {
      try {
        ToolDriver record = new ToolDriver();
        record.exe = fieldSetFlags()[0] ? this.exe : (java.lang.CharSequence) defaultValue(fields()[0]);
        record.serialization = fieldSetFlags()[1] ? this.serialization : (java.lang.CharSequence) defaultValue(fields()[1]);
        return record;
      } catch (Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  private static final org.apache.avro.io.DatumWriter
    WRITER$ = new org.apache.avro.specific.SpecificDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  private static final org.apache.avro.io.DatumReader
    READER$ = new org.apache.avro.specific.SpecificDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

}

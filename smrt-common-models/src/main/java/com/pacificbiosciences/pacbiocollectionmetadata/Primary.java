//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2016.04.06 at 11:45:18 AM PDT 
//


package com.pacificbiosciences.pacbiocollectionmetadata;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="SampleTrace" minOccurs="0">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="TraceSamplingFactor" type="{http://www.w3.org/2001/XMLSchema}float"/>
 *                   &lt;element name="FullPulseFile" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
 *                 &lt;/sequence>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *         &lt;element name="AutomationName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="ConfigFileName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="SequencingCondition" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="OutputOptions">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="ResultsFolder" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *                   &lt;element name="CollectionPathUri" type="{http://www.w3.org/2001/XMLSchema}anyURI"/>
 *                   &lt;element name="CopyFiles">
 *                     &lt;complexType>
 *                       &lt;complexContent>
 *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                           &lt;sequence>
 *                             &lt;element name="CollectionFileCopy" type="{http://pacificbiosciences.com/PacBioCollectionMetadata.xsd}PapOutputFile" maxOccurs="unbounded"/>
 *                           &lt;/sequence>
 *                         &lt;/restriction>
 *                       &lt;/complexContent>
 *                     &lt;/complexType>
 *                   &lt;/element>
 *                   &lt;element name="Readout">
 *                     &lt;simpleType>
 *                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *                         &lt;enumeration value="Pulses"/>
 *                         &lt;enumeration value="Bases"/>
 *                         &lt;enumeration value="Bases_Without_QVs"/>
 *                       &lt;/restriction>
 *                     &lt;/simpleType>
 *                   &lt;/element>
 *                   &lt;element name="MetricsVerbosity">
 *                     &lt;simpleType>
 *                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *                         &lt;enumeration value="Minimal"/>
 *                         &lt;enumeration value="High"/>
 *                         &lt;enumeration value="None"/>
 *                       &lt;/restriction>
 *                     &lt;/simpleType>
 *                   &lt;/element>
 *                   &lt;element name="TransferResource" minOccurs="0">
 *                     &lt;complexType>
 *                       &lt;complexContent>
 *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                           &lt;sequence>
 *                             &lt;element name="Id" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *                             &lt;element name="TransferScheme">
 *                               &lt;simpleType>
 *                                 &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *                                   &lt;enumeration value="RSYNC"/>
 *                                   &lt;enumeration value="SRS"/>
 *                                   &lt;enumeration value="NFS"/>
 *                                 &lt;/restriction>
 *                               &lt;/simpleType>
 *                             &lt;/element>
 *                             &lt;element name="Name" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *                             &lt;element name="Description" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *                           &lt;/sequence>
 *                         &lt;/restriction>
 *                       &lt;/complexContent>
 *                     &lt;/complexType>
 *                   &lt;/element>
 *                 &lt;/sequence>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "sampleTrace",
    "automationName",
    "configFileName",
    "sequencingCondition",
    "outputOptions"
})
@XmlRootElement(name = "Primary")
public class Primary {

    @XmlElement(name = "SampleTrace")
    protected Primary.SampleTrace sampleTrace;
    @XmlElement(name = "AutomationName", required = true)
    protected String automationName;
    @XmlElement(name = "ConfigFileName", required = true)
    protected String configFileName;
    @XmlElement(name = "SequencingCondition", required = true)
    protected String sequencingCondition;
    @XmlElement(name = "OutputOptions", required = true)
    protected Primary.OutputOptions outputOptions;

    /**
     * Gets the value of the sampleTrace property.
     * 
     * @return
     *     possible object is
     *     {@link Primary.SampleTrace }
     *     
     */
    public Primary.SampleTrace getSampleTrace() {
        return sampleTrace;
    }

    /**
     * Sets the value of the sampleTrace property.
     * 
     * @param value
     *     allowed object is
     *     {@link Primary.SampleTrace }
     *     
     */
    public void setSampleTrace(Primary.SampleTrace value) {
        this.sampleTrace = value;
    }

    /**
     * Gets the value of the automationName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAutomationName() {
        return automationName;
    }

    /**
     * Sets the value of the automationName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAutomationName(String value) {
        this.automationName = value;
    }

    /**
     * Gets the value of the configFileName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getConfigFileName() {
        return configFileName;
    }

    /**
     * Sets the value of the configFileName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setConfigFileName(String value) {
        this.configFileName = value;
    }

    /**
     * Gets the value of the sequencingCondition property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSequencingCondition() {
        return sequencingCondition;
    }

    /**
     * Sets the value of the sequencingCondition property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSequencingCondition(String value) {
        this.sequencingCondition = value;
    }

    /**
     * Gets the value of the outputOptions property.
     * 
     * @return
     *     possible object is
     *     {@link Primary.OutputOptions }
     *     
     */
    public Primary.OutputOptions getOutputOptions() {
        return outputOptions;
    }

    /**
     * Sets the value of the outputOptions property.
     * 
     * @param value
     *     allowed object is
     *     {@link Primary.OutputOptions }
     *     
     */
    public void setOutputOptions(Primary.OutputOptions value) {
        this.outputOptions = value;
    }


    /**
     * <p>Java class for anonymous complex type.
     * 
     * <p>The following schema fragment specifies the expected content contained within this class.
     * 
     * <pre>
     * &lt;complexType>
     *   &lt;complexContent>
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *       &lt;sequence>
     *         &lt;element name="ResultsFolder" type="{http://www.w3.org/2001/XMLSchema}string"/>
     *         &lt;element name="CollectionPathUri" type="{http://www.w3.org/2001/XMLSchema}anyURI"/>
     *         &lt;element name="CopyFiles">
     *           &lt;complexType>
     *             &lt;complexContent>
     *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *                 &lt;sequence>
     *                   &lt;element name="CollectionFileCopy" type="{http://pacificbiosciences.com/PacBioCollectionMetadata.xsd}PapOutputFile" maxOccurs="unbounded"/>
     *                 &lt;/sequence>
     *               &lt;/restriction>
     *             &lt;/complexContent>
     *           &lt;/complexType>
     *         &lt;/element>
     *         &lt;element name="Readout">
     *           &lt;simpleType>
     *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
     *               &lt;enumeration value="Pulses"/>
     *               &lt;enumeration value="Bases"/>
     *               &lt;enumeration value="Bases_Without_QVs"/>
     *             &lt;/restriction>
     *           &lt;/simpleType>
     *         &lt;/element>
     *         &lt;element name="MetricsVerbosity">
     *           &lt;simpleType>
     *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
     *               &lt;enumeration value="Minimal"/>
     *               &lt;enumeration value="High"/>
     *               &lt;enumeration value="None"/>
     *             &lt;/restriction>
     *           &lt;/simpleType>
     *         &lt;/element>
     *         &lt;element name="TransferResource" minOccurs="0">
     *           &lt;complexType>
     *             &lt;complexContent>
     *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *                 &lt;sequence>
     *                   &lt;element name="Id" type="{http://www.w3.org/2001/XMLSchema}string"/>
     *                   &lt;element name="TransferScheme">
     *                     &lt;simpleType>
     *                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
     *                         &lt;enumeration value="RSYNC"/>
     *                         &lt;enumeration value="SRS"/>
     *                         &lt;enumeration value="NFS"/>
     *                       &lt;/restriction>
     *                     &lt;/simpleType>
     *                   &lt;/element>
     *                   &lt;element name="Name" type="{http://www.w3.org/2001/XMLSchema}string"/>
     *                   &lt;element name="Description" type="{http://www.w3.org/2001/XMLSchema}string"/>
     *                 &lt;/sequence>
     *               &lt;/restriction>
     *             &lt;/complexContent>
     *           &lt;/complexType>
     *         &lt;/element>
     *       &lt;/sequence>
     *     &lt;/restriction>
     *   &lt;/complexContent>
     * &lt;/complexType>
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "resultsFolder",
        "collectionPathUri",
        "copyFiles",
        "readout",
        "metricsVerbosity",
        "transferResource"
    })
    public static class OutputOptions {

        @XmlElement(name = "ResultsFolder", required = true)
        protected String resultsFolder;
        @XmlElement(name = "CollectionPathUri", required = true)
        @XmlSchemaType(name = "anyURI")
        protected String collectionPathUri;
        @XmlElement(name = "CopyFiles", required = true)
        protected Primary.OutputOptions.CopyFiles copyFiles;
        @XmlElement(name = "Readout", required = true, defaultValue = "Bases")
        protected String readout;
        @XmlElement(name = "MetricsVerbosity", required = true, defaultValue = "Minimal")
        protected String metricsVerbosity;
        @XmlElement(name = "TransferResource")
        protected Primary.OutputOptions.TransferResource transferResource;

        /**
         * Gets the value of the resultsFolder property.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getResultsFolder() {
            return resultsFolder;
        }

        /**
         * Sets the value of the resultsFolder property.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setResultsFolder(String value) {
            this.resultsFolder = value;
        }

        /**
         * Gets the value of the collectionPathUri property.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getCollectionPathUri() {
            return collectionPathUri;
        }

        /**
         * Sets the value of the collectionPathUri property.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setCollectionPathUri(String value) {
            this.collectionPathUri = value;
        }

        /**
         * Gets the value of the copyFiles property.
         * 
         * @return
         *     possible object is
         *     {@link Primary.OutputOptions.CopyFiles }
         *     
         */
        public Primary.OutputOptions.CopyFiles getCopyFiles() {
            return copyFiles;
        }

        /**
         * Sets the value of the copyFiles property.
         * 
         * @param value
         *     allowed object is
         *     {@link Primary.OutputOptions.CopyFiles }
         *     
         */
        public void setCopyFiles(Primary.OutputOptions.CopyFiles value) {
            this.copyFiles = value;
        }

        /**
         * Gets the value of the readout property.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getReadout() {
            return readout;
        }

        /**
         * Sets the value of the readout property.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setReadout(String value) {
            this.readout = value;
        }

        /**
         * Gets the value of the metricsVerbosity property.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getMetricsVerbosity() {
            return metricsVerbosity;
        }

        /**
         * Sets the value of the metricsVerbosity property.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setMetricsVerbosity(String value) {
            this.metricsVerbosity = value;
        }

        /**
         * Gets the value of the transferResource property.
         * 
         * @return
         *     possible object is
         *     {@link Primary.OutputOptions.TransferResource }
         *     
         */
        public Primary.OutputOptions.TransferResource getTransferResource() {
            return transferResource;
        }

        /**
         * Sets the value of the transferResource property.
         * 
         * @param value
         *     allowed object is
         *     {@link Primary.OutputOptions.TransferResource }
         *     
         */
        public void setTransferResource(Primary.OutputOptions.TransferResource value) {
            this.transferResource = value;
        }


        /**
         * <p>Java class for anonymous complex type.
         * 
         * <p>The following schema fragment specifies the expected content contained within this class.
         * 
         * <pre>
         * &lt;complexType>
         *   &lt;complexContent>
         *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
         *       &lt;sequence>
         *         &lt;element name="CollectionFileCopy" type="{http://pacificbiosciences.com/PacBioCollectionMetadata.xsd}PapOutputFile" maxOccurs="unbounded"/>
         *       &lt;/sequence>
         *     &lt;/restriction>
         *   &lt;/complexContent>
         * &lt;/complexType>
         * </pre>
         * 
         * 
         */
        @XmlAccessorType(XmlAccessType.FIELD)
        @XmlType(name = "", propOrder = {
            "collectionFileCopy"
        })
        public static class CopyFiles {

            @XmlElement(name = "CollectionFileCopy", required = true)
            @XmlSchemaType(name = "string")
            protected List<PapOutputFile> collectionFileCopy;

            /**
             * Gets the value of the collectionFileCopy property.
             * 
             * <p>
             * This accessor method returns a reference to the live list,
             * not a snapshot. Therefore any modification you make to the
             * returned list will be present inside the JAXB object.
             * This is why there is not a <CODE>set</CODE> method for the collectionFileCopy property.
             * 
             * <p>
             * For example, to add a new item, do as follows:
             * <pre>
             *    getCollectionFileCopy().add(newItem);
             * </pre>
             * 
             * 
             * <p>
             * Objects of the following type(s) are allowed in the list
             * {@link PapOutputFile }
             * 
             * 
             */
            public List<PapOutputFile> getCollectionFileCopy() {
                if (collectionFileCopy == null) {
                    collectionFileCopy = new ArrayList<PapOutputFile>();
                }
                return this.collectionFileCopy;
            }

        }


        /**
         * <p>Java class for anonymous complex type.
         * 
         * <p>The following schema fragment specifies the expected content contained within this class.
         * 
         * <pre>
         * &lt;complexType>
         *   &lt;complexContent>
         *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
         *       &lt;sequence>
         *         &lt;element name="Id" type="{http://www.w3.org/2001/XMLSchema}string"/>
         *         &lt;element name="TransferScheme">
         *           &lt;simpleType>
         *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
         *               &lt;enumeration value="RSYNC"/>
         *               &lt;enumeration value="SRS"/>
         *               &lt;enumeration value="NFS"/>
         *             &lt;/restriction>
         *           &lt;/simpleType>
         *         &lt;/element>
         *         &lt;element name="Name" type="{http://www.w3.org/2001/XMLSchema}string"/>
         *         &lt;element name="Description" type="{http://www.w3.org/2001/XMLSchema}string"/>
         *       &lt;/sequence>
         *     &lt;/restriction>
         *   &lt;/complexContent>
         * &lt;/complexType>
         * </pre>
         * 
         * 
         */
        @XmlAccessorType(XmlAccessType.FIELD)
        @XmlType(name = "", propOrder = {
            "id",
            "transferScheme",
            "name",
            "description"
        })
        public static class TransferResource {

            @XmlElement(name = "Id", required = true)
            protected String id;
            @XmlElement(name = "TransferScheme", required = true)
            protected String transferScheme;
            @XmlElement(name = "Name", required = true)
            protected String name;
            @XmlElement(name = "Description", required = true)
            protected String description;

            /**
             * Gets the value of the id property.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getId() {
                return id;
            }

            /**
             * Sets the value of the id property.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setId(String value) {
                this.id = value;
            }

            /**
             * Gets the value of the transferScheme property.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getTransferScheme() {
                return transferScheme;
            }

            /**
             * Sets the value of the transferScheme property.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setTransferScheme(String value) {
                this.transferScheme = value;
            }

            /**
             * Gets the value of the name property.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getName() {
                return name;
            }

            /**
             * Sets the value of the name property.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setName(String value) {
                this.name = value;
            }

            /**
             * Gets the value of the description property.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getDescription() {
                return description;
            }

            /**
             * Sets the value of the description property.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setDescription(String value) {
                this.description = value;
            }

        }

    }


    /**
     * <p>Java class for anonymous complex type.
     * 
     * <p>The following schema fragment specifies the expected content contained within this class.
     * 
     * <pre>
     * &lt;complexType>
     *   &lt;complexContent>
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *       &lt;sequence>
     *         &lt;element name="TraceSamplingFactor" type="{http://www.w3.org/2001/XMLSchema}float"/>
     *         &lt;element name="FullPulseFile" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
     *       &lt;/sequence>
     *     &lt;/restriction>
     *   &lt;/complexContent>
     * &lt;/complexType>
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "traceSamplingFactor",
        "fullPulseFile"
    })
    public static class SampleTrace {

        @XmlElement(name = "TraceSamplingFactor")
        protected float traceSamplingFactor;
        @XmlElement(name = "FullPulseFile")
        protected boolean fullPulseFile;

        /**
         * Gets the value of the traceSamplingFactor property.
         * 
         */
        public float getTraceSamplingFactor() {
            return traceSamplingFactor;
        }

        /**
         * Sets the value of the traceSamplingFactor property.
         * 
         */
        public void setTraceSamplingFactor(float value) {
            this.traceSamplingFactor = value;
        }

        /**
         * Gets the value of the fullPulseFile property.
         * 
         */
        public boolean isFullPulseFile() {
            return fullPulseFile;
        }

        /**
         * Sets the value of the fullPulseFile property.
         * 
         */
        public void setFullPulseFile(boolean value) {
            this.fullPulseFile = value;
        }

    }

}

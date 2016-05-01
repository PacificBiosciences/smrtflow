//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2016.04.06 at 11:45:18 AM PDT 
//


package com.pacificbiosciences.pacbiobasedatamodel;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for IndexedDataType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="IndexedDataType">
 *   &lt;complexContent>
 *     &lt;extension base="{http://pacificbiosciences.com/PacBioBaseDataModel.xsd}InputOutputDataType">
 *       &lt;sequence>
 *         &lt;element name="FileIndices" minOccurs="0">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="FileIndex" type="{http://pacificbiosciences.com/PacBioBaseDataModel.xsd}InputOutputDataType" maxOccurs="unbounded"/>
 *                 &lt;/sequence>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *         &lt;element ref="{http://pacificbiosciences.com/PacBioBaseDataModel.xsd}ExternalResources" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "IndexedDataType", propOrder = {
    "fileIndices",
    "externalResources"
})
@XmlSeeAlso({
    ExternalResource.class
})
public class IndexedDataType
    extends InputOutputDataType
{

    @XmlElement(name = "FileIndices")
    protected IndexedDataType.FileIndices fileIndices;
    @XmlElement(name = "ExternalResources")
    protected ExternalResources externalResources;

    /**
     * Gets the value of the fileIndices property.
     * 
     * @return
     *     possible object is
     *     {@link IndexedDataType.FileIndices }
     *     
     */
    public IndexedDataType.FileIndices getFileIndices() {
        return fileIndices;
    }

    /**
     * Sets the value of the fileIndices property.
     * 
     * @param value
     *     allowed object is
     *     {@link IndexedDataType.FileIndices }
     *     
     */
    public void setFileIndices(IndexedDataType.FileIndices value) {
        this.fileIndices = value;
    }

    /**
     * Gets the value of the externalResources property.
     * 
     * @return
     *     possible object is
     *     {@link ExternalResources }
     *     
     */
    public ExternalResources getExternalResources() {
        return externalResources;
    }

    /**
     * Sets the value of the externalResources property.
     * 
     * @param value
     *     allowed object is
     *     {@link ExternalResources }
     *     
     */
    public void setExternalResources(ExternalResources value) {
        this.externalResources = value;
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
     *         &lt;element name="FileIndex" type="{http://pacificbiosciences.com/PacBioBaseDataModel.xsd}InputOutputDataType" maxOccurs="unbounded"/>
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
        "fileIndex"
    })
    public static class FileIndices {

        @XmlElement(name = "FileIndex", required = true)
        protected List<InputOutputDataType> fileIndex;

        /**
         * Gets the value of the fileIndex property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the fileIndex property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getFileIndex().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link InputOutputDataType }
         * 
         * 
         */
        public List<InputOutputDataType> getFileIndex() {
            if (fileIndex == null) {
                fileIndex = new ArrayList<InputOutputDataType>();
            }
            return this.fileIndex;
        }

    }

}

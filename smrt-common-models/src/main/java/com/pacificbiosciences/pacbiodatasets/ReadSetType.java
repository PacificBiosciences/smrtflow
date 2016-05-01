//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2016.04.06 at 11:45:18 AM PDT 
//


package com.pacificbiosciences.pacbiodatasets;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * Type for DataSets consisting of unaligned subreads and CCS reads DataSets
 * 
 * <p>Java class for ReadSetType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ReadSetType">
 *   &lt;complexContent>
 *     &lt;extension base="{http://pacificbiosciences.com/PacBioDatasets.xsd}DataSetType">
 *       &lt;sequence>
 *         &lt;element name="DataSetMetadata" type="{http://pacificbiosciences.com/PacBioDatasets.xsd}ReadSetMetadataType" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ReadSetType", propOrder = {
    "dataSetMetadata"
})
@XmlSeeAlso({
    HdfSubreadSet.class,
    SubreadSetType.class
})
public class ReadSetType
    extends DataSetType
{

    @XmlElement(name = "DataSetMetadata")
    protected ReadSetMetadataType dataSetMetadata;

    /**
     * Gets the value of the dataSetMetadata property.
     * 
     * @return
     *     possible object is
     *     {@link ReadSetMetadataType }
     *     
     */
    public ReadSetMetadataType getDataSetMetadata() {
        return dataSetMetadata;
    }

    /**
     * Sets the value of the dataSetMetadata property.
     * 
     * @param value
     *     allowed object is
     *     {@link ReadSetMetadataType }
     *     
     */
    public void setDataSetMetadata(ReadSetMetadataType value) {
        this.dataSetMetadata = value;
    }

}

//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2017.05.17 at 02:46:42 PM PDT 
//


package com.pacificbiosciences.pacbiodatasets;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * Type for DataSets consisting of aligned subreads and CCS reads.
 * 
 * <p>Java class for AlignmentSetType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AlignmentSetType">
 *   &lt;complexContent>
 *     &lt;extension base="{http://pacificbiosciences.com/PacBioDatasets.xsd}DataSetType">
 *       &lt;sequence>
 *         &lt;element name="DataSetMetadata" type="{http://pacificbiosciences.com/PacBioDatasets.xsd}AlignmentSetMetadataType" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AlignmentSetType", propOrder = {
    "dataSetMetadata"
})
@XmlSeeAlso({
    AlignmentSet.class,
    ConsensusAlignmentSet.class
})
public class AlignmentSetType
    extends DataSetType
{

    @XmlElement(name = "DataSetMetadata")
    protected AlignmentSetMetadataType dataSetMetadata;

    /**
     * Gets the value of the dataSetMetadata property.
     * 
     * @return
     *     possible object is
     *     {@link AlignmentSetMetadataType }
     *     
     */
    public AlignmentSetMetadataType getDataSetMetadata() {
        return dataSetMetadata;
    }

    /**
     * Sets the value of the dataSetMetadata property.
     * 
     * @param value
     *     allowed object is
     *     {@link AlignmentSetMetadataType }
     *     
     */
    public void setDataSetMetadata(AlignmentSetMetadataType value) {
        this.dataSetMetadata = value;
    }

}

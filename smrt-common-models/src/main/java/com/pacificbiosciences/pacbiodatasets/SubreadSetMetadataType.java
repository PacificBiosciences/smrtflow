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
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SubreadSetMetadataType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SubreadSetMetadataType">
 *   &lt;complexContent>
 *     &lt;extension base="{http://pacificbiosciences.com/PacBioDatasets.xsd}DataSetMetadataType">
 *       &lt;sequence>
 *         &lt;element name="AverageSubreadLength" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="AverageSubreadQuality" type="{http://www.w3.org/2001/XMLSchema}float"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SubreadSetMetadataType", propOrder = {
    "averageSubreadLength",
    "averageSubreadQuality"
})
public class SubreadSetMetadataType
    extends DataSetMetadataType
{

    @XmlElement(name = "AverageSubreadLength")
    protected int averageSubreadLength;
    @XmlElement(name = "AverageSubreadQuality")
    protected float averageSubreadQuality;

    /**
     * Gets the value of the averageSubreadLength property.
     * 
     */
    public int getAverageSubreadLength() {
        return averageSubreadLength;
    }

    /**
     * Sets the value of the averageSubreadLength property.
     * 
     */
    public void setAverageSubreadLength(int value) {
        this.averageSubreadLength = value;
    }

    /**
     * Gets the value of the averageSubreadQuality property.
     * 
     */
    public float getAverageSubreadQuality() {
        return averageSubreadQuality;
    }

    /**
     * Sets the value of the averageSubreadQuality property.
     * 
     */
    public void setAverageSubreadQuality(float value) {
        this.averageSubreadQuality = value;
    }

}

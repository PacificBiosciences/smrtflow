//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2017.09.18 at 03:16:30 PM PDT 
//


package com.pacificbiosciences.pacbiodatamodel;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
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
 *         &lt;element name="ExperimentContainer" type="{http://pacificbiosciences.com/PacBioDataModel.xsd}ExperimentContainerType"/>
 *         &lt;any minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="Version" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "experimentContainer",
    "any"
})
@XmlRootElement(name = "PacBioDataModel")
public class PacBioDataModel {

    @XmlElement(name = "ExperimentContainer", required = true)
    protected ExperimentContainerType experimentContainer;
    @XmlAnyElement(lax = true)
    protected Object any;
    @XmlAttribute(name = "Version")
    protected String version;

    /**
     * Gets the value of the experimentContainer property.
     * 
     * @return
     *     possible object is
     *     {@link ExperimentContainerType }
     *     
     */
    public ExperimentContainerType getExperimentContainer() {
        return experimentContainer;
    }

    /**
     * Sets the value of the experimentContainer property.
     * 
     * @param value
     *     allowed object is
     *     {@link ExperimentContainerType }
     *     
     */
    public void setExperimentContainer(ExperimentContainerType value) {
        this.experimentContainer = value;
    }

    /**
     * Gets the value of the any property.
     * 
     * @return
     *     possible object is
     *     {@link Object }
     *     
     */
    public Object getAny() {
        return any;
    }

    /**
     * Sets the value of the any property.
     * 
     * @param value
     *     allowed object is
     *     {@link Object }
     *     
     */
    public void setAny(Object value) {
        this.any = value;
    }

    /**
     * Gets the value of the version property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the value of the version property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVersion(String value) {
        this.version = value;
    }

}

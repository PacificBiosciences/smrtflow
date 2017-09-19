//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2017.09.18 at 03:16:30 PM PDT 
//


package com.pacificbiosciences.pacbiobasedatamodel;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlType;


/**
 * Represents the DNA control complex. 
 * 
 * <p>Java class for SupplyKitControl complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SupplyKitControl">
 *   &lt;complexContent>
 *     &lt;extension base="{http://pacificbiosciences.com/PacBioBaseDataModel.xsd}PartNumberType">
 *       &lt;sequence>
 *         &lt;element name="InternalControlName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="CustomSequence" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SupplyKitControl", propOrder = {
    "internalControlName",
    "customSequence"
})
public class SupplyKitControl
    extends PartNumberType
{

    @XmlElement(name = "InternalControlName")
    protected String internalControlName;
    @XmlElementRef(name = "CustomSequence", namespace = "http://pacificbiosciences.com/PacBioBaseDataModel.xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> customSequence;

    /**
     * Gets the value of the internalControlName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getInternalControlName() {
        return internalControlName;
    }

    /**
     * Sets the value of the internalControlName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setInternalControlName(String value) {
        this.internalControlName = value;
    }

    /**
     * Gets the value of the customSequence property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getCustomSequence() {
        return customSequence;
    }

    /**
     * Sets the value of the customSequence property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setCustomSequence(JAXBElement<String> value) {
        this.customSequence = value;
    }

}

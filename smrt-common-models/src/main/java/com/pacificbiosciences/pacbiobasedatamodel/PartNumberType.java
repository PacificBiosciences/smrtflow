//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: XXX
//


package com.pacificbiosciences.pacbiobasedatamodel;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;
import com.pacificbiosciences.pacbiopartnumbers.OtherKit;
import com.pacificbiosciences.pacbioreagentkit.SupplyKitSequencing;


/**
 * Generic representation of a supply kit. 
 * 
 * If the part number has an NFC associated with it, the contents of the NFC may be encoded here.
 * 
 * <p>Java class for PartNumberType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PartNumberType">
 *   &lt;complexContent>
 *     &lt;extension base="{http://pacificbiosciences.com/PacBioBaseDataModel.xsd}DataEntityType">
 *       &lt;sequence>
 *         &lt;element ref="{http://pacificbiosciences.com/PacBioBaseDataModel.xsd}Defaults" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="PartNumber" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="LotNumber" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="Barcode" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="ExpirationDate" type="{http://www.w3.org/2001/XMLSchema}date" />
 *       &lt;attribute name="IsObsolete" type="{http://www.w3.org/2001/XMLSchema}boolean" default="false" />
 *       &lt;attribute name="IsRestricted" type="{http://www.w3.org/2001/XMLSchema}boolean" default="false" />
 *       &lt;attribute name="ChipType" type="{http://pacificbiosciences.com/PacBioBaseDataModel.xsd}SupportedChipTypes" default="AllChips" />
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PartNumberType", propOrder = {
    "defaults"
})
@XmlSeeAlso({
    SupplyKitCellPack.class,
    SupplyKitControl.class,
    SupplyKitTemplate.class,
    SupplyKitBinding.class,
    SupplyKitSequencing.class,
    OtherKit.class
})
public class PartNumberType
    extends DataEntityType
{

    @XmlElement(name = "Defaults", nillable = true)
    protected DefaultsType defaults;
    @XmlAttribute(name = "PartNumber")
    protected String partNumber;
    @XmlAttribute(name = "LotNumber")
    protected String lotNumber;
    @XmlAttribute(name = "Barcode")
    protected String barcode;
    @XmlAttribute(name = "ExpirationDate")
    @XmlSchemaType(name = "date")
    protected XMLGregorianCalendar expirationDate;
    @XmlAttribute(name = "IsObsolete")
    protected Boolean isObsolete;
    @XmlAttribute(name = "IsRestricted")
    protected Boolean isRestricted;
    @XmlAttribute(name = "ChipType")
    protected SupportedChipTypes chipType;

    /**
     * Gets the value of the defaults property.
     * 
     * @return
     *     possible object is
     *     {@link DefaultsType }
     *     
     */
    public DefaultsType getDefaults() {
        return defaults;
    }

    /**
     * Sets the value of the defaults property.
     * 
     * @param value
     *     allowed object is
     *     {@link DefaultsType }
     *     
     */
    public void setDefaults(DefaultsType value) {
        this.defaults = value;
    }

    /**
     * Gets the value of the partNumber property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPartNumber() {
        return partNumber;
    }

    /**
     * Sets the value of the partNumber property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPartNumber(String value) {
        this.partNumber = value;
    }

    /**
     * Gets the value of the lotNumber property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLotNumber() {
        return lotNumber;
    }

    /**
     * Sets the value of the lotNumber property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLotNumber(String value) {
        this.lotNumber = value;
    }

    /**
     * Gets the value of the barcode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getBarcode() {
        return barcode;
    }

    /**
     * Sets the value of the barcode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setBarcode(String value) {
        this.barcode = value;
    }

    /**
     * Gets the value of the expirationDate property.
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getExpirationDate() {
        return expirationDate;
    }

    /**
     * Sets the value of the expirationDate property.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setExpirationDate(XMLGregorianCalendar value) {
        this.expirationDate = value;
    }

    /**
     * Gets the value of the isObsolete property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public boolean isIsObsolete() {
        if (isObsolete == null) {
            return false;
        } else {
            return isObsolete;
        }
    }

    /**
     * Sets the value of the isObsolete property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setIsObsolete(Boolean value) {
        this.isObsolete = value;
    }

    /**
     * Gets the value of the isRestricted property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public boolean isIsRestricted() {
        if (isRestricted == null) {
            return false;
        } else {
            return isRestricted;
        }
    }

    /**
     * Sets the value of the isRestricted property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setIsRestricted(Boolean value) {
        this.isRestricted = value;
    }

    /**
     * Gets the value of the chipType property.
     * 
     * @return
     *     possible object is
     *     {@link SupportedChipTypes }
     *     
     */
    public SupportedChipTypes getChipType() {
        if (chipType == null) {
            return SupportedChipTypes.ALL_CHIPS;
        } else {
            return chipType;
        }
    }

    /**
     * Sets the value of the chipType property.
     * 
     * @param value
     *     allowed object is
     *     {@link SupportedChipTypes }
     *     
     */
    public void setChipType(SupportedChipTypes value) {
        this.chipType = value;
    }

}

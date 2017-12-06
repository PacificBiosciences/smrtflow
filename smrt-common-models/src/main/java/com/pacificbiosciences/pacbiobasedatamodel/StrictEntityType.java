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
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import com.pacificbiosciences.pacbiocollectionmetadata.CollectionMetadata;
import com.pacificbiosciences.pacbiodatamodel.RunType;
import com.pacificbiosciences.pacbiodatasets.DataSetType;
import com.pacificbiosciences.pacbiodatasets.SubsetType;
import com.pacificbiosciences.pacbiorightsandroles.AccessRightType;
import com.pacificbiosciences.pacbiorightsandroles.AuditableEventType;
import com.pacificbiosciences.pacbiorightsandroles.RoleType;
import com.pacificbiosciences.pacbiorightsandroles.UserIdentityType;


/**
 * This is the base element type for all types in this data model
 * 
 * <p>Java class for StrictEntityType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="StrictEntityType">
 *   &lt;complexContent>
 *     &lt;extension base="{http://pacificbiosciences.com/PacBioBaseDataModel.xsd}BaseEntityType">
 *       &lt;attribute name="UniqueId" use="required">
 *         &lt;simpleType>
 *           &lt;restriction base="{http://www.w3.org/2001/XMLSchema}ID">
 *             &lt;pattern value="[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"/>
 *           &lt;/restriction>
 *         &lt;/simpleType>
 *       &lt;/attribute>
 *       &lt;attribute name="MetaType" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="TimeStampedName" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "StrictEntityType")
@XmlSeeAlso({
    SubsetType.class,
    RunType.class,
    UserIdentityType.class,
    AuditableEventType.class,
    AccessRightType.class,
    RoleType.class,
    CollectionMetadata.class,
    InputOutputDataType.class,
    DataSetType.class
})
public class StrictEntityType
    extends BaseEntityType
{

    @XmlAttribute(name = "UniqueId", required = true)
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    protected String uniqueId;
    @XmlAttribute(name = "MetaType", required = true)
    protected String metaType;
    @XmlAttribute(name = "TimeStampedName", required = true)
    protected String timeStampedName;

    /**
     * Gets the value of the uniqueId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUniqueId() {
        return uniqueId;
    }

    /**
     * Sets the value of the uniqueId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUniqueId(String value) {
        this.uniqueId = value;
    }

    /**
     * Gets the value of the metaType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMetaType() {
        return metaType;
    }

    /**
     * Sets the value of the metaType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMetaType(String value) {
        this.metaType = value;
    }

    /**
     * Gets the value of the timeStampedName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTimeStampedName() {
        return timeStampedName;
    }

    /**
     * Sets the value of the timeStampedName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTimeStampedName(String value) {
        this.timeStampedName = value;
    }

}

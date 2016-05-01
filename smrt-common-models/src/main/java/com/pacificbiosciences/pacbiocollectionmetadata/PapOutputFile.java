//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2016.04.06 at 11:45:18 AM PDT 
//


package com.pacificbiosciences.pacbiocollectionmetadata;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PapOutputFile.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PapOutputFile">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="None"/>
 *     &lt;enumeration value="Trace"/>
 *     &lt;enumeration value="Fasta"/>
 *     &lt;enumeration value="Baz"/>
 *     &lt;enumeration value="Bam"/>
 *     &lt;enumeration value="DarkFrame"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "PapOutputFile")
@XmlEnum
public enum PapOutputFile {

    @XmlEnumValue("None")
    NONE("None"),
    @XmlEnumValue("Trace")
    TRACE("Trace"),
    @XmlEnumValue("Fasta")
    FASTA("Fasta"),
    @XmlEnumValue("Baz")
    BAZ("Baz"),
    @XmlEnumValue("Bam")
    BAM("Bam"),
    @XmlEnumValue("DarkFrame")
    DARK_FRAME("DarkFrame");
    private final String value;

    PapOutputFile(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static PapOutputFile fromValue(String v) {
        for (PapOutputFile c: PapOutputFile.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}

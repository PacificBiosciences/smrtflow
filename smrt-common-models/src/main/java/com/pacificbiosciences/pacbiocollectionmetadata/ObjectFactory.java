//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2017.11.07 at 07:18:35 AM PST 
//


package com.pacificbiosciences.pacbiocollectionmetadata;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;
import com.pacificbiosciences.pacbiobasedatamodel.UserDefinedFieldsType;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.pacificbiosciences.pacbiocollectionmetadata package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _UserDefinedFields_QNAME = new QName("http://pacificbiosciences.com/PacBioCollectionMetadata.xsd", "UserDefinedFields");
    private final static QName _ExpirationDataSequencingTube0PastExpiration_QNAME = new QName("http://pacificbiosciences.com/PacBioCollectionMetadata.xsd", "SequencingTube0PastExpiration");
    private final static QName _ExpirationDataTemplatePrepKitPastExpiration_QNAME = new QName("http://pacificbiosciences.com/PacBioCollectionMetadata.xsd", "TemplatePrepKitPastExpiration");
    private final static QName _ExpirationDataSequencingTube1PastExpiration_QNAME = new QName("http://pacificbiosciences.com/PacBioCollectionMetadata.xsd", "SequencingTube1PastExpiration");
    private final static QName _ExpirationDataCellPacPastExpiration_QNAME = new QName("http://pacificbiosciences.com/PacBioCollectionMetadata.xsd", "CellPacPastExpiration");
    private final static QName _ExpirationDataSequencingKitPastExpiration_QNAME = new QName("http://pacificbiosciences.com/PacBioCollectionMetadata.xsd", "SequencingKitPastExpiration");
    private final static QName _ExpirationDataBindingKitPastExpiration_QNAME = new QName("http://pacificbiosciences.com/PacBioCollectionMetadata.xsd", "BindingKitPastExpiration");
    private final static QName _CollectionMetadataComponentVersions_QNAME = new QName("http://pacificbiosciences.com/PacBioCollectionMetadata.xsd", "ComponentVersions");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.pacificbiosciences.pacbiocollectionmetadata
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link Primary }
     * 
     */
    public Primary createPrimary() {
        return new Primary();
    }

    /**
     * Create an instance of {@link Secondary }
     * 
     */
    public Secondary createSecondary() {
        return new Secondary();
    }

    /**
     * Create an instance of {@link CollectionMetadata }
     * 
     */
    public CollectionMetadata createCollectionMetadata() {
        return new CollectionMetadata();
    }

    /**
     * Create an instance of {@link Primary.OutputOptions }
     * 
     */
    public Primary.OutputOptions createPrimaryOutputOptions() {
        return new Primary.OutputOptions();
    }

    /**
     * Create an instance of {@link ExpirationData }
     * 
     */
    public ExpirationData createExpirationData() {
        return new ExpirationData();
    }

    /**
     * Create an instance of {@link Movie }
     * 
     */
    public Movie createMovie() {
        return new Movie();
    }

    /**
     * Create an instance of {@link RunDetails }
     * 
     */
    public RunDetails createRunDetails() {
        return new RunDetails();
    }

    /**
     * Create an instance of {@link WellSample }
     * 
     */
    public WellSample createWellSample() {
        return new WellSample();
    }

    /**
     * Create an instance of {@link Primary.SampleTrace }
     * 
     */
    public Primary.SampleTrace createPrimarySampleTrace() {
        return new Primary.SampleTrace();
    }

    /**
     * Create an instance of {@link Secondary.AutomationParameters }
     * 
     */
    public Secondary.AutomationParameters createSecondaryAutomationParameters() {
        return new Secondary.AutomationParameters();
    }

    /**
     * Create an instance of {@link CollectionMetadata.ComponentVersions }
     * 
     */
    public CollectionMetadata.ComponentVersions createCollectionMetadataComponentVersions() {
        return new CollectionMetadata.ComponentVersions();
    }

    /**
     * Create an instance of {@link KeyValue }
     * 
     */
    public KeyValue createKeyValue() {
        return new KeyValue();
    }

    /**
     * Create an instance of {@link PacBioCollectionMetadata }
     * 
     */
    public PacBioCollectionMetadata createPacBioCollectionMetadata() {
        return new PacBioCollectionMetadata();
    }

    /**
     * Create an instance of {@link Collections }
     * 
     */
    public Collections createCollections() {
        return new Collections();
    }

    /**
     * Create an instance of {@link Primary.OutputOptions.CopyFiles }
     * 
     */
    public Primary.OutputOptions.CopyFiles createPrimaryOutputOptionsCopyFiles() {
        return new Primary.OutputOptions.CopyFiles();
    }

    /**
     * Create an instance of {@link Primary.OutputOptions.TransferResource }
     * 
     */
    public Primary.OutputOptions.TransferResource createPrimaryOutputOptionsTransferResource() {
        return new Primary.OutputOptions.TransferResource();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link UserDefinedFieldsType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioCollectionMetadata.xsd", name = "UserDefinedFields")
    public JAXBElement<UserDefinedFieldsType> createUserDefinedFields(UserDefinedFieldsType value) {
        return new JAXBElement<UserDefinedFieldsType>(_UserDefinedFields_QNAME, UserDefinedFieldsType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Integer }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioCollectionMetadata.xsd", name = "SequencingTube0PastExpiration", scope = ExpirationData.class)
    public JAXBElement<Integer> createExpirationDataSequencingTube0PastExpiration(Integer value) {
        return new JAXBElement<Integer>(_ExpirationDataSequencingTube0PastExpiration_QNAME, Integer.class, ExpirationData.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Integer }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioCollectionMetadata.xsd", name = "TemplatePrepKitPastExpiration", scope = ExpirationData.class)
    public JAXBElement<Integer> createExpirationDataTemplatePrepKitPastExpiration(Integer value) {
        return new JAXBElement<Integer>(_ExpirationDataTemplatePrepKitPastExpiration_QNAME, Integer.class, ExpirationData.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Integer }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioCollectionMetadata.xsd", name = "SequencingTube1PastExpiration", scope = ExpirationData.class)
    public JAXBElement<Integer> createExpirationDataSequencingTube1PastExpiration(Integer value) {
        return new JAXBElement<Integer>(_ExpirationDataSequencingTube1PastExpiration_QNAME, Integer.class, ExpirationData.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Integer }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioCollectionMetadata.xsd", name = "CellPacPastExpiration", scope = ExpirationData.class)
    public JAXBElement<Integer> createExpirationDataCellPacPastExpiration(Integer value) {
        return new JAXBElement<Integer>(_ExpirationDataCellPacPastExpiration_QNAME, Integer.class, ExpirationData.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Integer }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioCollectionMetadata.xsd", name = "SequencingKitPastExpiration", scope = ExpirationData.class)
    public JAXBElement<Integer> createExpirationDataSequencingKitPastExpiration(Integer value) {
        return new JAXBElement<Integer>(_ExpirationDataSequencingKitPastExpiration_QNAME, Integer.class, ExpirationData.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Integer }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioCollectionMetadata.xsd", name = "BindingKitPastExpiration", scope = ExpirationData.class)
    public JAXBElement<Integer> createExpirationDataBindingKitPastExpiration(Integer value) {
        return new JAXBElement<Integer>(_ExpirationDataBindingKitPastExpiration_QNAME, Integer.class, ExpirationData.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CollectionMetadata.ComponentVersions }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioCollectionMetadata.xsd", name = "ComponentVersions", scope = CollectionMetadata.class)
    public JAXBElement<CollectionMetadata.ComponentVersions> createCollectionMetadataComponentVersions(CollectionMetadata.ComponentVersions value) {
        return new JAXBElement<CollectionMetadata.ComponentVersions>(_CollectionMetadataComponentVersions_QNAME, CollectionMetadata.ComponentVersions.class, CollectionMetadata.class, value);
    }

}

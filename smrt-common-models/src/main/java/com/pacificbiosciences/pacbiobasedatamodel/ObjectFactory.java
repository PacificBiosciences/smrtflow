//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: XXX
//


package com.pacificbiosciences.pacbiobasedatamodel;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.pacificbiosciences.pacbiobasedatamodel package. 
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

    private final static QName _CellMaxMovieTimes_QNAME = new QName("http://pacificbiosciences.com/PacBioBaseDataModel.xsd", "CellMaxMovieTimes");
    private final static QName _Defaults_QNAME = new QName("http://pacificbiosciences.com/PacBioBaseDataModel.xsd", "Defaults");
    private final static QName _ValueDataType_QNAME = new QName("http://pacificbiosciences.com/PacBioBaseDataModel.xsd", "ValueDataType");
    private final static QName _AutomationParameter_QNAME = new QName("http://pacificbiosciences.com/PacBioBaseDataModel.xsd", "AutomationParameter");
    private final static QName _ChemistryConfig_QNAME = new QName("http://pacificbiosciences.com/PacBioBaseDataModel.xsd", "ChemistryConfig");
    private final static QName _KeyValueMap_QNAME = new QName("http://pacificbiosciences.com/PacBioBaseDataModel.xsd", "KeyValueMap");
    private final static QName _DataEntity_QNAME = new QName("http://pacificbiosciences.com/PacBioBaseDataModel.xsd", "DataEntity");
    private final static QName _ExtensionElement_QNAME = new QName("http://pacificbiosciences.com/PacBioBaseDataModel.xsd", "ExtensionElement");
    private final static QName _DataPointersDataPointer_QNAME = new QName("http://pacificbiosciences.com/PacBioBaseDataModel.xsd", "DataPointer");
    private final static QName _SupplyKitControlCustomSequence_QNAME = new QName("http://pacificbiosciences.com/PacBioBaseDataModel.xsd", "CustomSequence");
    private final static QName _DefaultsTypeFilters_QNAME = new QName("http://pacificbiosciences.com/PacBioBaseDataModel.xsd", "Filters");
    private final static QName _DefaultsTypeAutomationParameters_QNAME = new QName("http://pacificbiosciences.com/PacBioBaseDataModel.xsd", "AutomationParameters");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.pacificbiosciences.pacbiobasedatamodel
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link BaseEntityType }
     * 
     */
    public BaseEntityType createBaseEntityType() {
        return new BaseEntityType();
    }

    /**
     * Create an instance of {@link AnalogType }
     * 
     */
    public AnalogType createAnalogType() {
        return new AnalogType();
    }

    /**
     * Create an instance of {@link IndexedDataType }
     * 
     */
    public IndexedDataType createIndexedDataType() {
        return new IndexedDataType();
    }

    /**
     * Create an instance of {@link AutomationConstraintType }
     * 
     */
    public AutomationConstraintType createAutomationConstraintType() {
        return new AutomationConstraintType();
    }

    /**
     * Create an instance of {@link SequencingChemistry }
     * 
     */
    public SequencingChemistry createSequencingChemistry() {
        return new SequencingChemistry();
    }

    /**
     * Create an instance of {@link SequencingChemistry.DyeSet }
     * 
     */
    public SequencingChemistry.DyeSet createSequencingChemistryDyeSet() {
        return new SequencingChemistry.DyeSet();
    }

    /**
     * Create an instance of {@link AutomationType }
     * 
     */
    public AutomationType createAutomationType() {
        return new AutomationType();
    }

    /**
     * Create an instance of {@link FilterType }
     * 
     */
    public FilterType createFilterType() {
        return new FilterType();
    }

    /**
     * Create an instance of {@link FilterType.Properties }
     * 
     */
    public FilterType.Properties createFilterTypeProperties() {
        return new FilterType.Properties();
    }

    /**
     * Create an instance of {@link StatsContinuousDistType }
     * 
     */
    public StatsContinuousDistType createStatsContinuousDistType() {
        return new StatsContinuousDistType();
    }

    /**
     * Create an instance of {@link StatsDiscreteDistType }
     * 
     */
    public StatsDiscreteDistType createStatsDiscreteDistType() {
        return new StatsDiscreteDistType();
    }

    /**
     * Create an instance of {@link StatsTimeSeriesType }
     * 
     */
    public StatsTimeSeriesType createStatsTimeSeriesType() {
        return new StatsTimeSeriesType();
    }

    /**
     * Create an instance of {@link DefaultsType }
     * 
     */
    public DefaultsType createDefaultsType() {
        return new DefaultsType();
    }

    /**
     * Create an instance of {@link KeyValueMap }
     * 
     */
    public KeyValueMap createKeyValueMap() {
        return new KeyValueMap();
    }

    /**
     * Create an instance of {@link SequencingChemistryConfig }
     * 
     */
    public SequencingChemistryConfig createSequencingChemistryConfig() {
        return new SequencingChemistryConfig();
    }

    /**
     * Create an instance of {@link SequencingChemistryConfig.ReferenceSpectrum }
     * 
     */
    public SequencingChemistryConfig.ReferenceSpectrum createSequencingChemistryConfigReferenceSpectrum() {
        return new SequencingChemistryConfig.ReferenceSpectrum();
    }

    /**
     * Create an instance of {@link AnalogType.Spectrum }
     * 
     */
    public AnalogType.Spectrum createAnalogTypeSpectrum() {
        return new AnalogType.Spectrum();
    }

    /**
     * Create an instance of {@link DyeSetAnalog }
     * 
     */
    public DyeSetAnalog createDyeSetAnalog() {
        return new DyeSetAnalog();
    }

    /**
     * Create an instance of {@link BaseEntityType.Extensions }
     * 
     */
    public BaseEntityType.Extensions createBaseEntityTypeExtensions() {
        return new BaseEntityType.Extensions();
    }

    /**
     * Create an instance of {@link ExternalResource }
     * 
     */
    public ExternalResource createExternalResource() {
        return new ExternalResource();
    }

    /**
     * Create an instance of {@link InputOutputDataType }
     * 
     */
    public InputOutputDataType createInputOutputDataType() {
        return new InputOutputDataType();
    }

    /**
     * Create an instance of {@link StrictEntityType }
     * 
     */
    public StrictEntityType createStrictEntityType() {
        return new StrictEntityType();
    }

    /**
     * Create an instance of {@link IndexedDataType.FileIndices }
     * 
     */
    public IndexedDataType.FileIndices createIndexedDataTypeFileIndices() {
        return new IndexedDataType.FileIndices();
    }

    /**
     * Create an instance of {@link ExternalResources }
     * 
     */
    public ExternalResources createExternalResources() {
        return new ExternalResources();
    }

    /**
     * Create an instance of {@link DataPointers }
     * 
     */
    public DataPointers createDataPointers() {
        return new DataPointers();
    }

    /**
     * Create an instance of {@link PacBioSequencingChemistry }
     * 
     */
    public PacBioSequencingChemistry createPacBioSequencingChemistry() {
        return new PacBioSequencingChemistry();
    }

    /**
     * Create an instance of {@link DataEntityType }
     * 
     */
    public DataEntityType createDataEntityType() {
        return new DataEntityType();
    }

    /**
     * Create an instance of {@link ConfigSetAnalog }
     * 
     */
    public ConfigSetAnalog createConfigSetAnalog() {
        return new ConfigSetAnalog();
    }

    /**
     * Create an instance of {@link SupplyKitCellPack }
     * 
     */
    public SupplyKitCellPack createSupplyKitCellPack() {
        return new SupplyKitCellPack();
    }

    /**
     * Create an instance of {@link MapType }
     * 
     */
    public MapType createMapType() {
        return new MapType();
    }

    /**
     * Create an instance of {@link MapItemType }
     * 
     */
    public MapItemType createMapItemType() {
        return new MapItemType();
    }

    /**
     * Create an instance of {@link SupplyKitControl }
     * 
     */
    public SupplyKitControl createSupplyKitControl() {
        return new SupplyKitControl();
    }

    /**
     * Create an instance of {@link DNABarcode }
     * 
     */
    public DNABarcode createDNABarcode() {
        return new DNABarcode();
    }

    /**
     * Create an instance of {@link SupplyKitTemplate }
     * 
     */
    public SupplyKitTemplate createSupplyKitTemplate() {
        return new SupplyKitTemplate();
    }

    /**
     * Create an instance of {@link UserDefinedFieldsType }
     * 
     */
    public UserDefinedFieldsType createUserDefinedFieldsType() {
        return new UserDefinedFieldsType();
    }

    /**
     * Create an instance of {@link SupplyKitBinding }
     * 
     */
    public SupplyKitBinding createSupplyKitBinding() {
        return new SupplyKitBinding();
    }

    /**
     * Create an instance of {@link IncompatiblePairType }
     * 
     */
    public IncompatiblePairType createIncompatiblePairType() {
        return new IncompatiblePairType();
    }

    /**
     * Create an instance of {@link RecordedEventType }
     * 
     */
    public RecordedEventType createRecordedEventType() {
        return new RecordedEventType();
    }

    /**
     * Create an instance of {@link PartNumberType }
     * 
     */
    public PartNumberType createPartNumberType() {
        return new PartNumberType();
    }

    /**
     * Create an instance of {@link AutomationConstraintType.Automations }
     * 
     */
    public AutomationConstraintType.Automations createAutomationConstraintTypeAutomations() {
        return new AutomationConstraintType.Automations();
    }

    /**
     * Create an instance of {@link SequencingChemistry.DyeSet.Analogs }
     * 
     */
    public SequencingChemistry.DyeSet.Analogs createSequencingChemistryDyeSetAnalogs() {
        return new SequencingChemistry.DyeSet.Analogs();
    }

    /**
     * Create an instance of {@link AutomationType.AutomationParameters }
     * 
     */
    public AutomationType.AutomationParameters createAutomationTypeAutomationParameters() {
        return new AutomationType.AutomationParameters();
    }

    /**
     * Create an instance of {@link FilterType.Properties.Property }
     * 
     */
    public FilterType.Properties.Property createFilterTypePropertiesProperty() {
        return new FilterType.Properties.Property();
    }

    /**
     * Create an instance of {@link StatsContinuousDistType.BinCounts }
     * 
     */
    public StatsContinuousDistType.BinCounts createStatsContinuousDistTypeBinCounts() {
        return new StatsContinuousDistType.BinCounts();
    }

    /**
     * Create an instance of {@link StatsDiscreteDistType.BinCounts }
     * 
     */
    public StatsDiscreteDistType.BinCounts createStatsDiscreteDistTypeBinCounts() {
        return new StatsDiscreteDistType.BinCounts();
    }

    /**
     * Create an instance of {@link StatsDiscreteDistType.BinLabels }
     * 
     */
    public StatsDiscreteDistType.BinLabels createStatsDiscreteDistTypeBinLabels() {
        return new StatsDiscreteDistType.BinLabels();
    }

    /**
     * Create an instance of {@link StatsTimeSeriesType.Values }
     * 
     */
    public StatsTimeSeriesType.Values createStatsTimeSeriesTypeValues() {
        return new StatsTimeSeriesType.Values();
    }

    /**
     * Create an instance of {@link DefaultsType.AutomationParameters }
     * 
     */
    public DefaultsType.AutomationParameters createDefaultsTypeAutomationParameters() {
        return new DefaultsType.AutomationParameters();
    }

    /**
     * Create an instance of {@link DefaultsType.Filters }
     * 
     */
    public DefaultsType.Filters createDefaultsTypeFilters() {
        return new DefaultsType.Filters();
    }

    /**
     * Create an instance of {@link KeyValueMap.Items }
     * 
     */
    public KeyValueMap.Items createKeyValueMapItems() {
        return new KeyValueMap.Items();
    }

    /**
     * Create an instance of {@link SequencingChemistryConfig.Analogs }
     * 
     */
    public SequencingChemistryConfig.Analogs createSequencingChemistryConfigAnalogs() {
        return new SequencingChemistryConfig.Analogs();
    }

    /**
     * Create an instance of {@link SequencingChemistryConfig.TargetSNR }
     * 
     */
    public SequencingChemistryConfig.TargetSNR createSequencingChemistryConfigTargetSNR() {
        return new SequencingChemistryConfig.TargetSNR();
    }

    /**
     * Create an instance of {@link SequencingChemistryConfig.ReferenceSpectrum.Values }
     * 
     */
    public SequencingChemistryConfig.ReferenceSpectrum.Values createSequencingChemistryConfigReferenceSpectrumValues() {
        return new SequencingChemistryConfig.ReferenceSpectrum.Values();
    }

    /**
     * Create an instance of {@link AnalogType.Spectrum.Values }
     * 
     */
    public AnalogType.Spectrum.Values createAnalogTypeSpectrumValues() {
        return new AnalogType.Spectrum.Values();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link KeyValueMap }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioBaseDataModel.xsd", name = "CellMaxMovieTimes")
    public JAXBElement<KeyValueMap> createCellMaxMovieTimes(KeyValueMap value) {
        return new JAXBElement<KeyValueMap>(_CellMaxMovieTimes_QNAME, KeyValueMap.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DefaultsType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioBaseDataModel.xsd", name = "Defaults")
    public JAXBElement<DefaultsType> createDefaults(DefaultsType value) {
        return new JAXBElement<DefaultsType>(_Defaults_QNAME, DefaultsType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SupportedDataTypes }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioBaseDataModel.xsd", name = "ValueDataType")
    public JAXBElement<SupportedDataTypes> createValueDataType(SupportedDataTypes value) {
        return new JAXBElement<SupportedDataTypes>(_ValueDataType_QNAME, SupportedDataTypes.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DataEntityType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioBaseDataModel.xsd", name = "AutomationParameter")
    public JAXBElement<DataEntityType> createAutomationParameter(DataEntityType value) {
        return new JAXBElement<DataEntityType>(_AutomationParameter_QNAME, DataEntityType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SequencingChemistryConfig }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioBaseDataModel.xsd", name = "ChemistryConfig")
    public JAXBElement<SequencingChemistryConfig> createChemistryConfig(SequencingChemistryConfig value) {
        return new JAXBElement<SequencingChemistryConfig>(_ChemistryConfig_QNAME, SequencingChemistryConfig.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link KeyValueMap }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioBaseDataModel.xsd", name = "KeyValueMap")
    public JAXBElement<KeyValueMap> createKeyValueMap(KeyValueMap value) {
        return new JAXBElement<KeyValueMap>(_KeyValueMap_QNAME, KeyValueMap.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DataEntityType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioBaseDataModel.xsd", name = "DataEntity")
    public JAXBElement<DataEntityType> createDataEntity(DataEntityType value) {
        return new JAXBElement<DataEntityType>(_DataEntity_QNAME, DataEntityType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Object }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioBaseDataModel.xsd", name = "ExtensionElement")
    public JAXBElement<Object> createExtensionElement(Object value) {
        return new JAXBElement<Object>(_ExtensionElement_QNAME, Object.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Object }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioBaseDataModel.xsd", name = "DataPointer", scope = DataPointers.class)
    @XmlIDREF
    public JAXBElement<Object> createDataPointersDataPointer(Object value) {
        return new JAXBElement<Object>(_DataPointersDataPointer_QNAME, Object.class, DataPointers.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioBaseDataModel.xsd", name = "CustomSequence", scope = SupplyKitControl.class)
    public JAXBElement<String> createSupplyKitControlCustomSequence(String value) {
        return new JAXBElement<String>(_SupplyKitControlCustomSequence_QNAME, String.class, SupplyKitControl.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DefaultsType.Filters }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioBaseDataModel.xsd", name = "Filters", scope = DefaultsType.class)
    public JAXBElement<DefaultsType.Filters> createDefaultsTypeFilters(DefaultsType.Filters value) {
        return new JAXBElement<DefaultsType.Filters>(_DefaultsTypeFilters_QNAME, DefaultsType.Filters.class, DefaultsType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DefaultsType.AutomationParameters }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://pacificbiosciences.com/PacBioBaseDataModel.xsd", name = "AutomationParameters", scope = DefaultsType.class)
    public JAXBElement<DefaultsType.AutomationParameters> createDefaultsTypeAutomationParameters(DefaultsType.AutomationParameters value) {
        return new JAXBElement<DefaultsType.AutomationParameters>(_DefaultsTypeAutomationParameters_QNAME, DefaultsType.AutomationParameters.class, DefaultsType.class, value);
    }

}

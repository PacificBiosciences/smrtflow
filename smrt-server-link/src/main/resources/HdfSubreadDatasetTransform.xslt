<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:fn="http://www.w3.org/2005/xpath-functions"
                xmlns="http://pacificbiosciences.com/PacBioDataModel.xsd"
                xmlns:uuid="java:java.util.UUID"
                xpath-default-namespace="http://pacificbiosciences.com/PAP/Metadata.xsd"
                xmlns:bax="http://whatever" 
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"

                xmlns:pbbase="http://pacificbiosciences.com/PacBioBaseDataModel.xsd"  
                xmlns:pbsample="http://pacificbiosciences.com/PacBioSampleInfo.xsd" 
                xmlns:pbmeta="http://pacificbiosciences.com/PacBioCollectionMetadata.xsd" 
                xmlns:pbds="http://pacificbiosciences.com/PacBioDatasets.xsd" 
                xsi:schemaLocation="http://www.w3.org/2005/xpath-functions ">

    <xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>

    <xsl:variable name="dataset_uid"   select="uuid:randomUUID()"/>
    <xsl:variable name="biosample_uid" select="uuid:randomUUID()"/>
    <xsl:variable name="date_time" select="fn:current-dateTime()"/>
    <xsl:variable name="movie_name" select="replace( tokenize( base-uri(), '/')[last()], '.metadata.xml', '')"/>

    <!-- Function to construct the bax file paths from the metadata.xml path-->
    <xsl:function name="bax:mybax">
        <xsl:param name="base_uri"/>
        <xsl:param name="results_folder"/>
        <xsl:param name="bax_index"/>

        <xsl:value-of
                select="concat(
            string-join(tokenize(
                replace($base_uri, 'file:/', 'file:///'), '/')[position() != last()], '/'),
                '/', $results_folder, 
                '/', $movie_name, '.', 
                $bax_index, 
                '.bax.h5')"/>
    </xsl:function>

    <xsl:template match="/">
        <xsl:element name="pbds:HdfSubreadSet">
            <xsl:attribute name="UniqueId"><xsl:value-of select="$dataset_uid"/></xsl:attribute>
            <xsl:attribute name="TimeStampedName">hdfsubreadset_<xsl:value-of select="$date_time"/></xsl:attribute>
            <xsl:attribute name="MetaType">PacBio.DataSet.HdfSubreadSet</xsl:attribute>
            <xsl:attribute name="Name">Subreads from <xsl:value-of select="Metadata/Sample/Name"/> - <xsl:value-of select="Metadata/Sample/WellName"/></xsl:attribute>
            <xsl:attribute name="Tags">pacbio.secondary.instrument=RS</xsl:attribute>
            <xsl:attribute name="Version">3.0.1</xsl:attribute>
            <xsl:element name="pbbase:ExternalResources">
                <xsl:element name="pbbase:ExternalResource">
                    <xsl:attribute name="UniqueId"><xsl:value-of select="uuid:randomUUID()"/></xsl:attribute>
                    <xsl:attribute name="TimeStampedName">hdfsubread_file_<xsl:value-of select="$date_time"/></xsl:attribute>
                    <xsl:attribute name="MetaType">PacBio.SubreadFile.BaxFile</xsl:attribute>
                    <xsl:attribute name="ResourceId">
                        <xsl:value-of select="bax:mybax( base-uri(), 'Analysis_Results', 1)"/>
                    </xsl:attribute>
                </xsl:element>
                <xsl:element name="pbbase:ExternalResource">
                    <xsl:attribute name="UniqueId"><xsl:value-of select="uuid:randomUUID()"/></xsl:attribute>
                    <xsl:attribute name="TimeStampedName">hdfsubread_file_<xsl:value-of select="$date_time"/></xsl:attribute>
                    <xsl:attribute name="MetaType">PacBio.SubreadFile.BaxFile</xsl:attribute>
                    <xsl:attribute name="ResourceId">
                        <xsl:value-of select="bax:mybax( base-uri(), 'Analysis_Results', 2)"/>
                    </xsl:attribute>
                </xsl:element>
                <xsl:element name="pbbase:ExternalResource">
                    <xsl:attribute name="UniqueId"><xsl:value-of select="uuid:randomUUID()"/></xsl:attribute>
                    <xsl:attribute name="TimeStampedName">hdfsubreadset_file_<xsl:value-of select="$date_time"/></xsl:attribute>
                    <xsl:attribute name="MetaType">PacBio.SubreadFile.BaxFile</xsl:attribute>
                    <xsl:attribute name="ResourceId">
                        <xsl:value-of select="bax:mybax( base-uri(), 'Analysis_Results', 3)"/>
                    </xsl:attribute>
                </xsl:element>

            </xsl:element>
            <xsl:element name="pbds:DataSetMetadata">
                <!-- TODO add this as a parameter? -->
                <xsl:element name="pbds:TotalLength">50000000</xsl:element>
                <xsl:element name="pbds:NumRecords">150000</xsl:element>
                <xsl:element name="pbmeta:Collections">
                    <xsl:element name="pbmeta:CollectionMetadata">
                        <xsl:attribute name="UniqueId"><xsl:value-of select="uuid:randomUUID()"/></xsl:attribute>
                        <xsl:attribute name="TimeStampedName"><xsl:value-of select="$movie_name"/></xsl:attribute>
                        <xsl:attribute name="MetaType">PacBio.Collection</xsl:attribute>
                        <xsl:attribute name="Context"><xsl:value-of select="$movie_name"/></xsl:attribute>
                        <xsl:attribute name="InstrumentName">
                            <xsl:value-of select="Metadata/InstrumentName"/>
                        </xsl:attribute>
                        <xsl:attribute name="InstrumentId">
                            <xsl:value-of select="Metadata/InstrumentId"/>
                        </xsl:attribute>
                        <xsl:element name="pbmeta:InstCtrlVer">
                            <xsl:value-of select="Metadata/InstCtrlVer"/>
                        </xsl:element>
                        <xsl:element name="pbmeta:SigProcVer">
                            <xsl:value-of select="Metadata/SigProcVer"/>
                        </xsl:element>
                        <xsl:element name="pbmeta:RunDetails">
                            <xsl:element name="pbmeta:TimeStampedName">
                                <xsl:value-of select="Metadata/Run/RunId"/>
                            </xsl:element>
                            <xsl:element name="pbmeta:Name">
                                <xsl:value-of select="Metadata/Run/Name"/>
                            </xsl:element>
                        </xsl:element>
                        <xsl:element name="pbmeta:WellSample">
                            <xsl:attribute name="Name">
                                <xsl:value-of select="Metadata/Sample/Name"/>
                            </xsl:attribute>
                            <xsl:element name="pbmeta:WellName">
                                <xsl:value-of select="Metadata/Sample/WellName"/>
                            </xsl:element>
                            <xsl:element name="pbmeta:Concentration">
                                <xsl:value-of select="Metadata/Sample/Concentration"/>
                            </xsl:element>
                            <xsl:element name="pbmeta:InsertSize">
                                <xsl:value-of select="Metadata/TemplatePrep/InsertSize"/>
                            </xsl:element>
                            <xsl:element name="pbmeta:SampleReuseEnabled">
                                <xsl:value-of select="Metadata/Sample/SampleReuseEnabled"/>
                            </xsl:element>
                            <xsl:element name="pbmeta:StageHotstartEnabled">
                                <xsl:value-of select="Metadata/Sample/StageHotstartEnabled"/>
                            </xsl:element>
                            <xsl:element name="pbmeta:SizeSelectionEnabled">
                                false
                            </xsl:element>
                            <xsl:element name="pbmeta:UseCount">
                                <xsl:value-of select="Metadata/Sample/UseCount"/>
                            </xsl:element>
                            <xsl:element name="pbsample:BioSamplePointers">
                                <xsl:element name="pbsample:BioSamplePointer">
                                    <xsl:value-of select="$biosample_uid"/>
                                </xsl:element>
                            </xsl:element>
                        </xsl:element>
                        <xsl:element name="pbmeta:Automation">
                            <xsl:element name="pbbase:AutomationParameters">
                                <xsl:element name="pbbase:AutomationParameter"/>
                            </xsl:element>
                        </xsl:element>
                        <xsl:element name="pbmeta:CollectionNumber">
                            <xsl:value-of select="Metadata/CollectionNumber"/>
                        </xsl:element>
                        <xsl:element name="pbmeta:CellIndex">
                            <xsl:value-of select="Metadata/CellIndex"/>
                        </xsl:element>
                        <xsl:element name="pbmeta:Primary">
                            <xsl:element name="pbmeta:AutomationName">
                                <xsl:value-of select="Metadata/Primary/Protocol"/>
                            </xsl:element>
                            <xsl:element name="pbmeta:ConfigFileName">
                                <xsl:value-of select="Metadata/Primary/ConfigFileName"/>
                            </xsl:element>
                            <xsl:element name="pbmeta:SequencingCondition"/>
                            <xsl:element name="pbmeta:OutputOptions">
                                <xsl:element name="pbmeta:ResultsFolder">
                                    <xsl:value-of select="Metadata/Primary/ResultsFolder"/>
                                </xsl:element>
                                <xsl:element name="pbmeta:CollectionPathUri">
                                    <xsl:value-of select="Metadata/Primary/CollectionPathUri"/>
                                </xsl:element>
                                <xsl:element name="pbmeta:CopyFiles">
                                    <xsl:element name="pbmeta:CollectionFileCopy">Fasta</xsl:element>
                                </xsl:element>
                                <xsl:element name="pbmeta:Readout">Bases</xsl:element>
                                <xsl:element name="pbmeta:MetricsVerbosity">Minimal</xsl:element>
                            </xsl:element>
                        </xsl:element>
                    </xsl:element>
                </xsl:element>
                <xsl:element name="pbsample:BioSamples">
                    <xsl:element name="pbsample:BioSample">
                        <xsl:attribute name="UniqueId"><xsl:value-of select="$biosample_uid"/></xsl:attribute>
                        <xsl:attribute name="TimeStampedName">biosample_<xsl:value-of select="$date_time"/></xsl:attribute>
                        <xsl:attribute name="MetaType">PacBio.Sample</xsl:attribute>
                        <xsl:attribute name="Name">
                            <xsl:value-of select="Metadata/Sample/Name"/>
                        </xsl:attribute>
                        <xsl:attribute name="Description">
                            <xsl:value-of select="Metadata/Sample/Comments"/>
                        </xsl:attribute>
                    </xsl:element>
                </xsl:element>
            </xsl:element>
        </xsl:element>
    </xsl:template>
</xsl:stylesheet>

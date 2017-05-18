//
// This is a template that can be used until this is automated.
// The namespace=X must correspond to the root namespace and
// the package X must be changed

@javax.xml.bind.annotation.XmlSchema(namespace = "http://pacificbiosciences.com/PacBioBaseDataModel.xsd",
        xmlns = {@XmlNs(prefix = "pbbase", namespaceURI = "http://pacificbiosciences.com/PacBioBaseDataModel.xsd"),
                @XmlNs(prefix = "pbdm", namespaceURI = "http://pacificbiosciences.com/PacBioDataModel.xsd"),
                @XmlNs(prefix = "pbds", namespaceURI = "http://pacificbiosciences.com/PacBioDatasets.xsd"),
                @XmlNs(prefix = "pbrk", namespaceURI = "http://pacificbiosciences.com/PacBioReagentKit.xsd"),
                @XmlNs(prefix = "pbsample", namespaceURI = "http://pacificbiosciences.com/PacBioSampleInfo.xsd"),
                @XmlNs(prefix = "pbpn", namespaceURI = "http://pacificbiosciences.com/PacBioPartNumbers.xsd"),
                @XmlNs(prefix = "pbmeta", namespaceURI = "http://pacificbiosciences.com/PacBioCollectionMetadata.xsd")

        },
        elementFormDefault = javax.xml.bind.annotation.XmlNsForm.QUALIFIED,
        attributeFormDefault = javax.xml.bind.annotation.XmlNsForm.UNQUALIFIED
)

package com.pacbio.primary.xsd;
import javax.xml.bind.annotation.XmlNs;

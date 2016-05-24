# Rebuild java classes from XSDs

Build java classes

To rebuild:

- pull XSDs from //common/datamodel/SequEl/EndToEnd/xsd/
- must delete PacBioDeclData.xsd and PacBioSeedingData.xsd (they have no namespace)
- call make
- update com.pacbio.common.pbmodels Constants.CHANGELIST and Constants.DATASET_VERSION
- run sanity tests


```bash
$>xjc src/main/resources/pb-common-xsds/ -d src/main/java/
```
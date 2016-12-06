# Common Models (aka "smrt-commson-models")

See [smrtflow.readthedocs.io](http://smrtflow.readthedocs.io/) for full docs and [smrtflow](../README.md) for the base multi-project's README. 

These are all the shared Java object models that are generated via [xjc](https://jaxb.java.net) from [XSD definitions in `src/main/resources/com/pacificbiosciences/pb-common-xsds`](src/main/resources/com/pacificbiosciences/pb-common-xsds).

## Rebuild java classes from XSDs

These only need to be remade when the definitions change. Currently this is done manually and not part of the build process.

```bash
# delete the old bindings
rm -fr src/main/java/*

# generate the new
xjc src/main/resources/pb-common-xsds/ -d src/main/java

# Update the XSDs stored within smrtflow
cp /path/to/xsds/*.xsd smrt-common-models/src/main/resources/pb-common-xsds/
````
FIXME(mpkocher)(2016-12-6) This needs to be automated. 


Make sure to update update [com.pacbio.common.pbmodels.Constants.CHANGELIST](src/main/scala/com/pacbio/common/models/Constants.scala#L10) and [DATASET_VERSION](src/main/scala/com/pacbio/common/models/Constants.scala#L11).

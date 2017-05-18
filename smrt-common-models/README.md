# Common Models (aka "smrt-common-models")

See [smrtflow.readthedocs.io](http://smrtflow.readthedocs.io/) for full docs and [smrtflow](../README.md) for the base multi-project's README. 

These are all the shared Java object models that are generated via [xjc](https://jaxb.java.net) from [XSD definitions in `src/main/resources/pb-common-xsds`](src/main/resources/pb-common-xsds).

Internal Location of XSD repo: http://bitbucket.nanofluidics.com:7990/projects/SEQ/repos/xsd-datamodels

# Rebuild java classes from XSDs

These only need to be remade when the definitions change. Currently this is done manually and not part of the build process.


## Step #1

Delete old XSDs and copy XSDs into smrt-common-models

```bash
# delete the old bindings
rm -fr smrt-common-model/src/main/java/*

# delete all XSDs
rm -rf smrt-common-modelsrc/main/resources/pb-common-xsds

# Perhaps need to delete PacBioDeclData.xsd or other non-namespaced files used by ICS
# Update the XSDs stored within smrtflow
cp xsd-datamodels/*.xsd smrt-common-model/src/main/resources/pb-common-xsds
```

## Step #2

Generate Java classes

```bash
# generate the new
xjc smrt-common-model/src/main/resources/pb-common-xsds/ -d smrt-common-model/src/main/java

````
## Step #3

## Manually FIX Namespaces

See extras/package-info.java and manually update the package-info.java classes.

Hacky way is use git to revert the package-info.java files in each subpackage. 

## Step #4

Update Constants.scala in smrt-common-models with the git SHA of the xsd-datamodels repo.

Make sure to update update [com.pacbio.common.pbmodels.Constants.CHANGELIST](src/main/scala/com/pacbio/common/models/Constants.scala#L13) and [DATASET_VERSION](src/main/scala/com/pacbio/common/models/Constants.scala#L11).

# FIXME(mpkocher)(2016-12-6) This needs to be automated. 

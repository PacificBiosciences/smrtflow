# Common Models (aka "smrt-common-models")

See [smrtflow.readthedocs.io](http://smrtflow.readthedocs.io/) for full docs and [smrtflow](../README.md) for the base multi-project's README. 

These are all the shared Java object models that are generated via [xjc](https://jaxb.java.net) from [XSD definitions in `src/main/resources/pb-common-xsds`](src/main/resources/pb-common-xsds).

Internal Location of XSD repo: http://bitbucket.nanofluidics.com:7990/projects/SEQ/repos/xsd-datamodels

# Rebuild java classes from XSDs

These only need to be remade when the definitions change. This is manually done using the `update-java-classes.sh` util.


## Step #1

Generate Java classes

```bash
# generate the new
cp /path/to/xsd-datamodels/*.xsd smrt-common-models/src/main/resources/pb-common-xsds
bash update-java-classes.sh
````

NOTE: If you have enum values are not valid java variable names, e.g. containing special characters or starting with numbers, you will first have to update [bindings.xml](/src/main/resources/bindings.xml)

## Step #2

Update Constants.scala in smrt-common-models with the git SHA of the xsd-datamodels repo.

Make sure to update update [com.pacbio.common.pbmodels.Constants.CHANGELIST](src/main/scala/com/pacbio/common/models/Constants.scala#L13)

**FIXME(mpkocher)(2016-12-6)** This still needs to be automated. 

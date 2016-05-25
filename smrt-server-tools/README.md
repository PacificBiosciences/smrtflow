# Tools (aka "smrt-server-tools")

See [smrtflow.readthedocs.io](http://smrtflow.readthedocs.io/) for full docs and [smrtflow](../README.md) for the base multi-project's README. 

Tools for working with the SMRT web services. See the `App` classes in [com/pacbio/secondary/smrttools](src/main/scala/com/pacbio/secondary/smrttools).
 
```bash
# example running GetStatus
sbt clean smrt-server-tools/pack
./smrt-server-tools/target/pack/bin/get-smrt-server-status --host smrtlink-bihourly --port 8081
``
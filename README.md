# smrtflow

[![Circle CI](https://circleci.com/gh/PacificBiosciences/smrtflow.svg?style=svg)](https://circleci.com/gh/PacificBiosciences/smrtflow)

**This README.md is intended to be terse notes** for developers. See [smrtflow.readthedocs.io](http://smrtflow.readthedocs.io/) for full docs.

"SMRT" refers to PacBio's [sequencing technology](http://www.pacb.com/smrt-science/smrt-sequencing/) and "smrtflow" is the name common models, Resolved Tool Contract/Tool Contract interface, commandline analysis tools, service orchestration engine and web services written in scala. PacBio pipelines can be run leveraging the PacBio workflow engine, [pbsmrtipe](https://github.com/PacificBiosciences/pbsmrtpipe) (must be installed).
 
This code is written in Scala and this is an [SBT multi-project](http://www.scala-sbt.org/0.13/docs/Multi-Project.html). 

Requires `java >= 1.8.0_71` and `sbt == 0.13.11`

## Building

```bash
# clone the repo
git clone https://github.com/PacificBiosciences/smrtflow.git

# use SBT to build and run tests
sbt clean pack test

# see also `sbt` + interactive `help`, `project`, `test`, `coverage`, `run`, ...
# sbt
# > help
```

## Build Commandline Tools

```bash
make tools
# or
sbt pack
```

Add tools to path

```bash
source setup-tools-env.sh
pbservice --help
fasta-to-reference --help
```

See the [full docs for details](http://smrtflow.readthedocs.io/) for details and examples of using SL tools, such as `pbservice` or `fasta-to-reference`.


## Runtime dependencies

Running postgres

On the cluster:
```bash
module load jdk/1.8.0_71 postgresql
export PGDATA=/localdisk/scratch/$USER/pg
mkdir -p $PGDATA
# on a shared machine, choose a PGPORT that's not already in use
export PGPORT=5442
initdb
perl -pi.orig -e "s/#port\s*=\s*(\d+)/port = $PGPORT/" $PGDATA/postgresql.conf
pg_ctl -l $PGDATA/postgresql.log start
createdb mskinner
psql < extras/db-init.sql
psql < extras/test-db-init.sql
export SMRTFLOW_DB_PORT=$PGPORT
```

to run tests, also do:
```bash
export SMRTFLOW_TEST_DB_PORT=$PGPORT
```

## Services

Launching SMRT Link/Analysis Services

```bash
sbt "smrt-server-analysis/run"
```

Set custom port

```bash
export PB_SERVICES_PORT=9997
sbt "smrt-server-analysis/run"
```

See the [full docs for details](http://smrtflow.readthedocs.io/)

See [reference.conf](https://github.com/PacificBiosciences/smrtflow/blob/master/smrt-server-link/src/main/resources/reference.conf) for more configuration parameters.

### Swagger Docs

The [SMRT Link Analysis Services](https://github.com/PacificBiosciences/smrtflow/blob/master/smrtlink_swagger.json) are documented using [Swagger Specification](http://swagger.io/specification/).

#### Validation of the swagger.json file

```
npm install swagger-tools
node_modules/swagger-cli/bin/swagger.js validate /path/to/smrtlink_swagger.json
```

#### UI Editor

[UI Editor](http://editor.swagger.io/#/) to import and edit the swagger file from a file or URL.

### PacBio Common Models

Many core data models are described using XSDs.

See [Resources Dir](https://github.com/PacificBiosciences/smrtflow/tree/master/smrt-common-models/src/main/resources/pb-common-xsds) for details.

See the [Readme](https://github.com/PacificBiosciences/smrtflow/blob/master/smrt-common-models/README.md) for generating the java classes from the XSDs.

Also see the common model (e.g., Report, ToolContract, DataStore, Pipeline, PipelineView Rules) [schemas here](https://github.com/PacificBiosciences/pbcommand/tree/master/pbcommand/schemas)

### REPL

Interactively load the smrtflow library code and execute expressions.

```
sbt smrtflow/test:console
@ import java.nio.file.Paths
import java.nio.file.Paths
@ val f = "/Users/mkocher/gh_mk_projects/smrtflow/PacBioTestData/data/SubreadSet/m54006_160504_020705.tiny.subreadset.xml"
f: String = "/Users/mkocher/gh_mk_projects/smrtflow/PacBioTestData/data/SubreadSet/m54006_160504_020705.tiny.subreadset.xml"
@ val px = Paths.get(f)
px: java.nio.file.Path = /Users/mkocher/gh_mk_projects/smrtflow/PacBioTestData/data/SubreadSet/m54006_160504_020705.tiny.subreadset.xml
@ import com.pacbio.secondary.analysis.datasets.io._
import com.pacbio.secondary.analysis.datasets.io._

@ val sset = DataSetLoader.loadSubreadSet(px)
sset: com.pacificbiosciences.pacbiodatasets.SubreadSet = com.pacificbiosciences.pacbiodatasets.SubreadSet@62c0ff68
@ sset.getName
res5: String = "subreads-sequel"

@ println("Services Example")
Services Example

@ import akka.actor.ActorSystem
import akka.actor.ActorSystem
@ implicit val actorSystem = ActorSystem("demo")
actorSystem: ActorSystem = akka://demo
@ import com.pacbio.secondary.smrtserver.client.{AnalysisServiceAccessLayer => Sal}
import com.pacbio.secondary.smrtserver.client.{AnalysisServiceAccessLayer => Sal}
@ val sal = new Sal("smrtlink-bihourly", 8081)
sal: com.pacbio.secondary.smrtserver.client.AnalysisServiceAccessLayer = com.pacbio.secondary.smrtserver.client.AnalysisServiceAccessLayer@8639ea4
@ val fx = sal.getStatus
fx: concurrent.Future[com.pacbio.common.models.ServiceStatus] = Success(ServiceStatus(smrtlink_analysis,Services have been up for 46 minutes and 59.472 seconds.,2819472,6d87566f-3433-4d73-8953-92673cc50f80,0.1.10-c63303e,secondarytest))
@ actorSystem.shutdown

@ exit
Bye!
welcomeBanner: Some[String] = Some(Welcome to the smrtflow REPL)
import ammonite.repl._
import ammonite.ops._
res0: Any = ()
Welcome to Scala 2.11.8 (Java HotSpot(TM) 64-Bit Server VM, Java 1.8.0_101).
Type in expressions for evaluation. Or try :help.

scala> :quit

```

### Integration testing

At a minimum, integration test analysis jobs requires installing pbsmrtpipe (in a virtualenv) to run a pbsmrtpipe analysis job. Specific pipeilnes will have dependencies on exes, such as `samtools` or `blasr`.

- install pbsmrtpipe in a VE
- enable scala tools via `make tools`
- add tools to path using `source setup-tools-env.sh` Test with `which pbservice ` or `pbservice --help`
- fetch PacBioTestData `make PacBioTestData`
- launch services `make start-smrt-server-analysis` or `make start-smrt-server-analysis-jar`
- import PacBioTestData `make import-pbdata`
- import canned ReferenceSet and SubreadSet `make test-int-import-data`
- run dev_diagnostic_stress test `make test-int-run-analysis-stress`


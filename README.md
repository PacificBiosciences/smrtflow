# smrtflow

[![Circle CI](https://circleci.com/gh/PacificBiosciences/smrtflow.svg?style=svg)](https://circleci.com/gh/PacificBiosciences/smrtflow)

See [smrtflow.readthedocs.io](http://smrtflow.readthedocs.io/) for full docs. This README.md is intended to be terse notes for developers.

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

See application.conf for configuration parameters.


See the ["Quick" Service docs](https://github.com/PacificBiosciences/smrtflow/tree/master/smrt-server-analysis#services-quick-docs) for Service endpoints defined in SMRT Link Analysis.

### REPL

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


### Swagger Docs

The [SMRT Link Analysis Services](https://github.com/PacificBiosciences/smrtflow/blob/master/smrtlink_swagger.json) are documented using [Swagger Specification](http://swagger.io/specification/).

Validation:

```
npm install swagger-tools
node_modules/swagger-cli/bin/swagger.js validate /path/to/smrtlink_swagger.json
```

Editor:

[UI Editor](http://editor.swagger.io/#/) to import and edit the swagger file from a file or URL.

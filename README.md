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
sbt test:console
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

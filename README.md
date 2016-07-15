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


### REPL



```
sbt test:console
```


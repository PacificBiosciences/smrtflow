# smrtflow

[![Circle CI](https://circleci.com/gh/PacificBiosciences/smrtflow.svg?style=svg)](https://circleci.com/gh/PacificBiosciences/smrtflow)


## Quick Start

- requires java >= 1.8.0_71
- requires sbt == 0.13.11

Build Commandline Tools

```bash
$> sbt smrt-analysis/pack 
$> export PATH=$(pwd)/smrt-analysis/target/pack/bin:$PATH
$> fasta-to-reference --help
```

Build SMRT Link Analysis Services

```bash
$>sbt smrt-server-analysis/assembly
$>x=$(ls ./smrt-server-analysis/target/scala-2.11/*.jar)
$>java -jar $x # to startup the services
```

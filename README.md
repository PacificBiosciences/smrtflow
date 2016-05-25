# smrtflow

[![Circle CI](https://circleci.com/gh/PacificBiosciences/smrtflow.svg?style=svg)](https://circleci.com/gh/PacificBiosciences/smrtflow)

See [smrtflow.readthedocs.io](http://smrtflow.readthedocs.io/) for full docs. This README.md is intended to be terse notes for developers.

"SMRT" refers to PacBio's [sequencing technology](http://www.pacb.com/smrt-science/smrt-sequencing/) and "smrtflow" is the name of the respective open-source workflow engine.
 
This code is written in Scala and this is an [SBT multi-project](http://www.scala-sbt.org/0.13/docs/Multi-Project.html). 

```bash
# clone the repo
git clone https://github.com/PacificBiosciences/smrtflow.git

# use SBT to build and run tests
sbt clean pack test

# see also `sbt` + interactive `help`, `project`, `test`, `coverage`, `run`, ...
# sbt
# > help
```

The code requires `java >= 1.8.0_71` and `sbt == 0.13.11`.
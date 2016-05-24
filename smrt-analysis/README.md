# Analysis Tools (aka "smrt-analysis")

See [smrtflow.readthedocs.io](http://smrtflow.readthedocs.io/) for full docs and [smrtflow](../README.md) for the base multi-project's README. 

Code in this sub-project includes the analysis API and related tools shared by the other projects. See the tests for example use and a tools.

- [FASTA](src/main/scala/com/pacbio/secondary/analysis/bio/Fasta.scala) - reading, writing, and converting FASTA files.
- Workflow Abstractions
  - [configloaders](src/main/scala/com/pacbio/secondary/analysis/configloaders/) - pipeline config
  - [constants](src/main/scala/com/pacbio/secondary/analysis/constants/) - shared constants
  - [contracts](src/main/scala/com/pacbio/secondary/analysis/contracts/) - interface for tools used by the pipline
  - [engine](src/main/scala/com/pacbio/secondary/analysis/engine/) - job abstractions and engine for running them
- Workflow Implementation
  - [Jobs](src/main/scala/com/pacbio/secondary/analysis/jobtypes/)
  - [Pipelines](src/main/scala/com/pacbio/secondary/analysis/pipelines/)
  - [Command-Line Tools](src/main/scala/com/pacbio/secondary/analysis/tools/), typically tools made from the jobs.
- [Bridge to python/cluster tasks](src/main/scala/com/pacbio/secondary/analysis/pbsmrtpipe/). See also [PacificBiosciences/pbsmrtpipe](https://github.com/PacificBiosciences/pbsmrtpipe).
  
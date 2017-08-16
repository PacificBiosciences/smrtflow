# Analysis Tools (aka "smrt-analysis")

See [smrtflow.readthedocs.io](http://smrtflow.readthedocs.io/) for full docs and [smrtflow](../README.md) for the base multi-project's README. 

Code in this sub-project includes the analysis API and related tools shared by the other projects. See the [tests](src/test/scala) for example use and a tools.

- [FASTA](../smrt-server-link/src/main/scala/com/pacbio/secondary/smrtlink/analysis/bio/Fasta.scala) - reading, writing, and converting FASTA files.
- Workflow Abstractions
  - [configloaders](../smrt-server-link/src/main/scala/com/pacbio/secondary/smrtlink/analysis/configloaders/) - pipeline config
  - [constants](../smrt-server-link/src/main/scala/com/pacbio/secondary/smrtlink/analysis/constants/) - shared constants
  - [contracts](../smrt-server-link/src/main/scala/com/pacbio/secondary/smrtlink/analysis/contracts/) - interface for tools used by the pipline
  - [engine](../smrt-server-link/src/main/scala/com/pacbio/secondary/smrtlink/analysis/engine/) - job abstractions and engine for running them
- Workflow Implementation
  - [Jobs](../smrt-server-link/src/main/scala/com/pacbio/secondary/smrtlink/analysis/jobtypes/)
  - [Pipelines](../smrt-server-link/src/main/scala/com/pacbio/secondary/smrtlink/analysis/pipelines/)
  - [Command-Line Tools](../smrt-server-link/src/main/scala/com/pacbio/secondary/smrtlink/analysis/tools/), typically tools made from the jobs.
- [Bridge to python/cluster tasks](../smrt-server-link/src/main/scala/com/pacbio/secondary/smrtlink/analysis/pbsmrtpipe/). See also [PacificBiosciences/pbsmrtpipe](https://github.com/PacificBiosciences/pbsmrtpipe).
  

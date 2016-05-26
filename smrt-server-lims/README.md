# LIMS and Resolution Service

See [smrtflow.readthedocs.io](http://smrtflow.readthedocs.io/) for full docs and [smrtflow](../README.md) for the base multi-project's README. 

Lab Information Managment System (LIMS) tracking and resolution service for common name or shorthand identifiers. See [smrtflow#89](https://github.com/PacificBiosciences/smrtflow/issues/89) for history.

```
# run the LIMS services independent of the greater codebase
sbt clean compile smrt-server-lims/run

```

You'll now have the service bound to port `8081`.


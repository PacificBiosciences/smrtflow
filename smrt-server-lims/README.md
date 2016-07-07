# SMRT Link Internal Analysis

Server for the internal PacBio analysis work. Historically, this code was in p4 and powered "Milhouse". The conversion
to Scala is part of a revamp to clean up the codebase and make it more maintainable.

- Slack Channel: [internalanalysis](https://pacbio.slack.com/messages/internalanalysis/details/)
- Bugzilla Tickets: "Internal" prefix under Secondary -> Internal SMRT Analysis. [Slack discussion link](https://pacbio.slack.com/archives/internalanalysis/p1467217687000317).

See [smrtflow.readthedocs.io](http://smrtflow.readthedocs.io/) for full docs and [smrtflow](../README.md) for the base multi-project's README. 

## LIMS and Resolution Service

Lab Information Managment System (LIMS) tracking and resolution service for common name or shorthand identifiers. See [smrtflow#89](https://github.com/PacificBiosciences/smrtflow/issues/89) for history.

Run the service via sbt.

```
# run the LIMS services independent of the greater codebase
sbt clean compile smrt-server-lims/run

```

You'll now have the service bound to port `8081`.


# Analysis Server (aka "smrt-server-analysis")

See [smrtflow.readthedocs.io](http://smrtflow.readthedocs.io/) for full docs and [smrtflow](../README.md) for the base multi-project's README. 

HTTP server for the web services that power the workflow engine and configurations for available piplines. These are the same services used by the web client that users typically interact with.

The main entry point for the server is [`com.pacbio.secondary.smrtserver.appcomponents.SecondaryAnalysisApp`](src/main/scala/com/pacbio/secondary/smrtserver/appcomponents/SecondaryAnalysisApp.scala).

```
# create the JAR via sbt
sbt smrt-server-analysis/assembly

# run the server
cd smrt-server-analysis
java -jar target/scala-2.11/smrt-server-analysis*.jar --debug
```

See also the [tests](test/scala/) for examples of code use. See also the templates for [pipelines](src/main/resources/pipline-template-view-rules) and [reports](src/main/resources/report-view-rules) supported. [Schemas](src/main/resources/schemas) too.
SMRT Link Analysis Services Config
==================================

Configuration is can be done using scala conf files, setting `-D` when launching the JVM, or by setting ENV vars.

Please see the `reference.conf` or the `application.conf` of each sbt subproject (e.g., smrt-server-analysis) for details.

Common Analysis Configuration
-----------------------------

- *PB_SERVICES_PORT* or *SMRTFLOW_SERVER_PORT* (smrtflow.server.port) Set the port to use
- *PB_SERVICES_MANIFEST_FILE* (smrtflow.server.manifestFile) Path to PacBioManifest file that contains versions of subcomponents, such as "smrtlink"
- *PB_ENGINE_JOB_ROOT* (smrtflow.engine.jobRootDir) Job root directory *PB_ENGINE_JOB_ROOT* (example: jobs-root, /path/to/jobs-root)
- *PB_SMRTPIPE_XML_PRESET* (smrtflow.engine.pbsmrtpipePresetXml) Path to default pbsmrtpipe Preset XML (example: /path/to/preset.xml)
- *PB_ENGINE_MAX_WORKERS* (smrtflow.engine.maxWorkers) Number of maximum services job workers to run concurrently. This will limit the total number of pbsmrtpipe, import-dataset, etc... jobs that are run concurrently.

For the db configuration see the hocon .conf files for details.


SMRT Link Bundle External Resources
-----------------------------------

- *PB_PIPELINE_TEMPLATE_DIR* Path to Resolved pipeline templates JSON files to load on startup
- *PB_RULES_REPORT_VIEW_DIR* Path to Report view rules JSON files
- *PB_RULES_PIPELINE_VIEW_DIR*  Path to Pipeline View Rule JSON files


.. note:: Multiple paths can be provided with a ":" separator. The order is important. Example `export PB_PIPELINE_TEMPLATE_DIR="/path/a:/path/b"`

See https://github.com/PacificBiosciences/pbpipeline-helloworld-resources for example SMRT Link External Resources and documentation.


Testing
-------


- *PB_TEST_DATA_FILES* Path to PacBioTestFiles repo (https://github.com/PacificBiosciences/PacBioTestData)
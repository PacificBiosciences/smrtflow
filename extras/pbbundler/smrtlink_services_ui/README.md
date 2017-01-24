# Run SMRT Link System

### SMRT Link is composed of 3 subcomponents

1. SMRT Link Analysis Web Services (scala/java services run from secondary-analysis.jar in the root of the bundle)
2. Tomcat webserver for serving static HTML files (the SL UI is server from)
3. The [WSO2 API Manager](http://wso2.com/products/api-manager/) to enforce user Rights and Roles

Note, SMRT View is started up and managed by the SL installer

### Bundle System Requirements

- vanilla python 2.7.x (for config parsing and processing)
- java 1.8 (oracle recommended)
- PostgreSQL 9.6.1 (SL Analysis Web services require). For stability and performance, the location of the database files *MUST* be stored on local disk (e.g., not on NFS).
- TODO (minimum recommended memory and number of cores)

### SL System bundle Commandline Tools

Several tools are included in the SL System bundle. There are internal or admin exes in *[BUNDLE_ROOT]/tools* and exes in *[BUNDLE_ROOT]/bin* that are configured to have specific defaults for getting the starting the bundle and getting the status of the bundle in [BUNDLE_ROOT]. 

#### Bin

Executables in [BUNDLE_ROOT]/bin/

- *start* Start the SL System
- *stop* Stop the SL System
- *get-status* Gets the status of SL System or gets the status of a subcomponent via `--subcomponent-id` TODO(mpkocher)(migrate to scala)
- *apply-config* Runs and Validates the SMRT Link System configuration JSON file (*smrtlink-system-config.json*) TODO(mpkocher)(migrate get-status to scala)


#### Tools

Executables in [BUNDLE_ROOT]/tools

- *pbservice* Interface to the SL Analysis web services. Get status of services, jobs, import datasets, etc...
- *smrt-db-tool* TODO(mpkocher)(Rename this tool) Db Run PostgreSQL database migrations, get the database connection status
- *amclient* Tool to access WSO2 API manager
- *bundler-validate-config* Validate Bundler SL System config JSON format (schema version 2 format)


##### Legacy Tools

- [BUNDLE_ROOT]/tools/bundler-migrate-legacy-db *Legacy SQLite* database migration tool to convert Sqlite (SL System 3.x to 4.0.0) to PostgreSQL 9.6 format
- [BUNDLE_ROOT]/tools/bundler-migrate-legacy-config Migration config.json (1.0) to (*smrtlink-system-config.json*) format
  

### Configuration

The public interface for configuration is the `smrtlink-system-config.json` in the [BUNDLE_ROOT] directory. This config file is *READ ONLY* from SL System.

The configuration is split into two root structures, "smrtflow.server" and "smrtflow.pacBioSystem". 

- smrtflow.server contains configuration for the SL Analysis subcomponent system 
- smrtflow.pacBioSystem contains shared subcomponent configuration (e.g., tmp directory) as well as subcomponent configuration for the SMRT View Port and the SL UI Tomcat Port. 

Please see the Avro Schema defined in the root of the bundle directory (e.g., [BUNDLE_ROOT]/SmrtLinkSystemConfig.avsc) for details on the allowed values of the keys. 

Generate documentation via `avrodoc SmrtLinkSystemConfig.avsc -o index.html` (avrodoc, not included in the bundle but [is available here](https://www.npmjs.com/package/avrodoc))

Special note, "smrtflow.server.dnsName" must be a externally accessible domain to enable *pbsmrtpipe* to communicate job status back to the SL Analysis Services and for *SL UI* to communicate with the SL Analysis Services via the WSO2 API Manager. If "smrtflow.server.dnsName" is None, the FQDN will be used.

Example:

```json
{
  "smrtflow": {
    "server": {
      "port": 8081,
      "manifestFile": null,
      "eventUrl": null,
      "dnsName": null
    },
    "engine": {
      "maxWorkers": 35,
      "jobRootDir": "./jobs-root",
      "pbsmrtpipePresetXml": "./pbsmrtpipe-default-preset.xml"
    },
    "db": {
      "properties": {
        "databaseName": "smrtlink",
        "user": "smrtlink_user",
        "password": "password",
        "portNumber": 5432,
        "serverName": "localhost"
      }
    }
  },
  "pacBioSystem": {
    "tomcatPort": 8080,
    "tomcatMemory": 1024,
    "smrtLinkServerMemoryMin": 4096,
    "smrtLinkServerMemoryMax": 4096,
    "smrtViewPort": 8084,
    "tmpDir": "/tmp",
    "logDir": "."
  },
  "comment": "SMRT Link System Default config example for Schema Version 2.0"
}
```

### SMRT Link System Subcomponent Logging and Output

- *SL Analysis Services* sl-analysis.pid will contain the process id of the SMRT Link Analysis web services
- *SL Analysis Services* sl-analysis.std{out|err} has the SMRT Link Analysis standard out and error. If there are errors starting the webservices, the stdout and stderr is the first place to look. The SL Analysis log file will contain further details.
- *SL Analysis log* file is configured in the "smrtflow.pacBioSystem.logDir" key
- *Tomcat UI Logs* file is in [BUNDLE_ROOT]/apache-tomcat-8.0.26/logs/
- *WSO2 API Manager logs* are in [BUNDLE_ROOT]/wso2am-2.0.0/repository/logs


### Change Log

#### Version 2.0.0

- Introduced in SL System 4.1.0
- Details TODO(mpkocher)

#### Version 1.0.0

- Used in SL System 3.0.x - 4.0.0

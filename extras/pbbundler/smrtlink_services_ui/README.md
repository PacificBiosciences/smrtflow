# Run SMRT Link System

(See the CHANGELOG.md for details of API changes)

### SMRT Link System is composed of 3 subcomponents

1. SL Installer. Contains the SL Installer, admin tools and layers to create the proper ENV for subcomponents and tools to be called
2. SL "pbbundle" subcomponents, SL Webservices, WSO2, Tomcat, admin commandline tools for interfacing to these components (described below)
3. SL Tools Commandline tools (e.g, blasr, pbsmrtpipe) used in analysis pipelines 


### SMRT Link pbbundle

The container component is referred to the **pbbundle** in this doc (FIXME. This needs a better name)

It contains and is managed four sub-components.

1. SMRT Link Analysis Web Services (scala/java services run from secondary-analysis.jar in the root of the bundle)
2. Tomcat webserver for serving static HTML files (the SL UI is server from)
3. The [WSO2 API Manager](http://wso2.com/products/api-manager/) to enforce user Rights and Roles
4. Postgres 9.6.1 DB used for SMRT Link Analysis services

Note, SMRT View subcomponent of SL System is started up and managed by the SL installer

### pbbundle System Requirements

- vanilla python 2.7.x (for config parsing and processing)
- java 1.8 (oracle recommended)
- PostgreSQL 9.6.1 (SL Analysis Web services require). For stability and performance, the location of the database files *MUST* be stored on local disk (e.g., not on NFS).
- TODO (clarify minimum recommended memory and number of cores)

### pbbundle Commandline Tools

Several tools are included in the SL System bundle. There are internal or admin exes in *[BUNDLE_ROOT]/tools/bin* and exes in *[BUNDLE_ROOT]/bin* that are configured to have specific defaults for getting the starting the bundle and getting the status of the bundle in [BUNDLE_ROOT]. 

#### Bin

Public Executables in [BUNDLE_ROOT]/bin

- *start* Start the SL System
- *stop* Stop the SL System
- *get-status* Gets the status of SL System or gets the status of a subcomponent via `--subcomponent-id`
- *apply-config* Runs and Validates the SMRT Link System configuration JSON file (*smrtlink-system-config.json*)
- *upgrade* This will start the db (if necessary), run the legacy migration, of import from SL == 4.0.0 Sqlite db into Postgres DB.(Note, only 4.0.0 importing is supported) 

Private Executables in [BUNDLE_ROOT]/bin (not be directly called from SL installer)

- *check-system-limits* Checks the system for required ulimits and other system related configuration
- *dbctrl* Handles Postgres db creation, initialization, start, stop, status, and verifying functionality
 

#### Tools

Executables in [BUNDLE_ROOT]/tools/bin

- *pbservice* Interface to the SL Analysis web services. Get status of services, jobs, import datasets, etc...
- *smrt-db-tool* TODO(mpkocher)(Rename this tool) Db Run PostgreSQL database migrations, get the database connection status
- *amclient* Tool to access WSO2 API manager
- *bundler-validate-config* Validate Bundler SL System config JSON format (schema version 2 format)


##### Legacy Tools

- [BUNDLE_ROOT]/tools/bin/bundler-migrate-legacy-db *Legacy SQLite* database migration tool to convert Sqlite (SL System 3.x to 4.0.0) to PostgreSQL 9.6 format
- [BUNDLE_ROOT]/tools/bin/bundler-migrate-legacy-config Migration config.json (1.0) to (*smrtlink-system-config.json*) format
  
  
### SMRT Link pbbundle Installing and Upgrading
   
**[BUNDLE_ROOT]/bin/upgrade** can be used to setup the SL System from a fresh/clean install, **or** an upgrade a system from a previous (N - 1) version of SMRT Link.

The upgrade script is configured by [BUNDLE_ROOT]/migration-config.json, which
has the following structure:

```json
{
    "PB_DB_URI": "/path/to/sqlite.file.db",
    "PREVIOUS_INSTALL_DIR": "/path/to/old/smrtlink/installation",
    "_comment": <optional comment>
}
```
**Note** this is NOT the same schema as the 4.0.0 config JSON

**Note** the SL 4.0.0 to SL 4.1.0 has a special case for handling the importing of the legacy 4.0.0 sqlite database to the 4.1.0 Postgres database.
   
A Summary of the import process

1. If Postgres db creation and users is not been initialized, it will
    - start up the db (if necessary)
    - run the creation and initialization
    - perform and Postgres to Postgres SQL migrations
    - shut down the db (if it was not originally running)
2. If the [BUNDLE_ROOT]/migration-config.json file exists, and has a non `null` for *PB_DB_URI*
    - Check for the [BUNDLE_ROOT]/legacy-migration.json
        - if the import was already successful, skip legacy import
        - else perform the SQLITE to Postgres importing/migration, then write the state to [BUNDLE_ROOT]/legacy-migration.json
    - [TODO] Clarify this. For failed SQLITE importing/migration, there is not currently a retry method (this would require dropping the tables)
3. shut down the db (if it was not originally running)

4. If [BUNDLE_ROOT]/migration-config.json exists and has a PREVIOUS_INSTALL_DIR that points to an existing directory,
    - start the wso2 from the old installation
    - pull out the user-role assignments
    - shut down the old wso2
5. Start the new wso2 and do initial configuration
    - create roles
    - configure API definitions
6. If we got user-role assignments in step 4,
    - import those into the new wso2
8. Shut down the new wso2


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

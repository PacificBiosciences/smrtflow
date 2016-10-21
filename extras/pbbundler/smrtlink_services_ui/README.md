# Build the UI and Services


The bundle requires java 1.8 (oracle recommended)

The bundle is configurable by **config.json** in the root level.

```
{
    "HOST": null,
    "PB_DB_URI": "./smrtlink_analysis_sqlite.db",
    "PB_SERVICES_ANALYSIS_MEM_MAX": 4096,
    "PB_SERVICES_ANALYSIS_MEM_MIN": 4096,
    "PB_SERVICES_ANALYSIS_PORT": 8070,
    "PB_SMRT_VIEW_PORT": 8084,
    "PB_JOB_ROOT": "./jobs-root",
    "PB_SERVICES_NWORKERS": 50,
    "PB_SERVICES_MEM": 1024,
    "PB_SERVICES_PORT": 8080,
    "PB_SMRTPIPE_PRESET_XML": "./pbsmrtpipe-default-preset.xml",
    "PB_TMP_DIR": "/tmp",
    "PB_LOG_DIR": ".",
    "PB_ANALYSIS_SHUTDOWN_PORT": 8005,
    "PB_ANALYSIS_AJP_PORT": 8009,
    "ENABLE_AUTH": false,
    "LDAP_HOST": "nanofluidics.com",
    "LDAP_USERS_DN": "CN=Users,DC=Nanofluidics,DC=com",
    "LDAP_USERS_EMAIL_ATTR": "mail",
    "LDAP_USERS_FIRST_NAME_ATTR": "givenName",
    "LDAP_USERS_LAST_NAME_ATTR": "sn",
    "_comment": "If the host is null, it will be set to the fqdn."
}
```

### Config details

**HOST** value of read-only to provide accessible from commandline tools

**PB_SERVICES_PORT** is the port that tomcat will listen on (i.e., the port that users will use in their browsers to access the web UI)

**PB_SERVICES_MEM** is the amount of heap memory in megabytes (java -Xmx and -Xms) for the tomcat UI static file server

**PB_SERVICES_ANALYSIS_PORT** is the port that Analysis Services will be run on.

**PB_SERVICES_ANALYSIS_MEM_MAX** is the max amount of heap memory in megabytes (java -Xmx) for the analysis services process

**PB_SERVICES_ANALYSIS_MEM_MIN** is the starting amount of heap memory in megabytes (java -Xms) for the analysis services process

**PB_MANIFEST_FILE** Option[Path] Is the path to the pacbio-manifest.json file that contains all the component metadata (e.g., name, version, description)

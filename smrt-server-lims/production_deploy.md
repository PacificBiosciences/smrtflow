# Production Deployment on smrt-lims

These are notes for the setup of a production server. Currently, HPC 
support maintains the servers, and there is no more formal server config
script (such as Ansible or Puppet) used. These are the steps to setup
smrt-lims from scratch.

For the most part, all important items are captured in scripts that have
comments explaining what they do. This doc mostly points to the scripts 
and notes where to run them on `smrt-lims`.

## Server VM and Working Directory

The box was requested from hpcsupport@pacificbiosciences.com. They'll
setup a VM dedicated to this service.

The code relies on a working directory. Currently it is setup as `~/jfalkner/smrt-lims`. Make this directory.

## Deploy

Code lives in git and changes are tracked in GitHub. See https://github.com/PacificBiosciences/smrtflow/tree/master/smrt-server-lims

The server must have a minimal set of tools available. At PacBio these
are all loaded via module.

- JDK 1.8+ = Java Development Kit. Latest is used. Older versions likely work fine. 
- sbt = Scala Build Tool
- git = Client for pulling the code from the public GitHub server

Use [deploy.sh](https://github.com/PacificBiosciences/smrtflow/blob/master/smrt-server-lims/scripts/deploy.sh)
and it'll perform a full build and start the server.

Notice that the script overrides the default `application.conf` with a
custom one named `production.conf` that lives in the working directory. 
It is the default config but with a JDBC URL that saves the database in the working directory.

```json
akka {
  loglevel = DEBUG
  event-handlers = ["akka.event.slf4j.Slf4jEventHandler"]
}

smrt-server-lims {
  jdbc-url = "jdbc:h2:/home/jfalkner/smrt-lims/production;CACHE_SIZE=100000"
  host = "0.0.0.0"
  port = 8070
}
```

Check http://smrt-lims:8070/status to confirm that the service is running.

## Auto-Updating Recently Changed lims.yml Files

Importing lims.yml files is the main thing the service does. Configure
the [find_and_load_recent_lims_yml.sh](https://github.com/PacificBiosciences/smrtflow/blob/master/smrt-server-lims/scripts/find_and_load_recent_lims_yml.sh)
to run hourly during the workday. A `crontab` example is in the script's comments.

The script assumes you'll have a working directory to save some temporary files. Make the `~/jfalkner/smrt-lims/hourly_import` directory.
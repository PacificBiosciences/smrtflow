# Logging

Simple logging for two common use cases in the PacBio tools.

1. LazyLogger-like mixin so that a `logger` val exists along with methods such as `info`, `debug`, `warn`, `error`, etc.
2. Conventional default config that can be altered per service via command-line flags.
  * Saves all logs (including Akka, Spray and smrtflow to disk)
  * `--debug` flag will dump logs to command-line, no files saved.
  * `--level` sets log level. Defaults to `WARN`.
  * `--file`  path to log file. Defaults to `./`

## Code Example

class MyServer extends LazyLogging {
  def apply (c: GetStatusConfig): Int = {
    // optional: update to logging config via command-line params
    updateLogging(c)
    // `logger` val is auto-created and config'd by the mixin
    logger.info("Starting Service...")
    ...
  }
}

## Command-Line Example

There are a few practical use cases that are supported. Not that you can ignore all of these option. A default, sane
config will be used. By default errors will be logged to stderr and everything else is discarded.

### Running a Production Server

You'll likely want to capture everything above warnings in a production environment. Use the `--loglevel` and `--logfile` flags.

```bash
./smrt-server-tools/target/pack/bin/get-smrt-server-status --host smrtlink-bihourly --port 8081 --loglevel WARN --logfile /var/log/my_log.log
```

In this case, the normal params exist and only these two alter the logging.

- `--loglevel` = Sets the logger handler to display all WARN level messages and worse.
- `--logfile` =  Where the logger will save data.

Users will almost always have a custom log location. Allowing this to be specified via command-line is a simple way to
support this versus requiring a custom log config file or property file.

### Dev Logging

When working on the code you probably always want to see errors on stderr. If that is true, you don't need to alter
anything. Just run the code.

If you are debugging a service and need verbose logging info, use the `--loglevel` param to set the level. `INFO` will
display the most information possible with all non-ERROR logs in stdout and ERROR in stderr. If you aren't piping the
output or you otherwise want to shunt it to a file then specify the `--logfile` param.

### Using a logback.xml config

It is possible to ignore all of the default conventions used by this API and rely on a standard logback.xml config file
via the `--logback`. This provides the most flexibility possible and relies on a well known, commonly used logging
library.

```bash
./smrt-server-tools/target/pack/bin/get-smrt-server-status --host smrtlink-bihourly --port 8081 --logback logback.xml
```
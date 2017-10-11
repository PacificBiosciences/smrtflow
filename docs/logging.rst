=====================
Logging configuration
=====================

Simple logging with params consistent with the PacBio tools. See [smrtflow#42](https://github.com/PacificBiosciences/smrtflow/pull/42) for history.

Usage
-----

Example command-line usage::

  $ ./smrt-server-link/target/pack/bin/pbservice -h
  ...
  Usage: ./app_with_logging [options]
  
  This is an app that supports PacBio logging flags. 
    -h | --help
          Show Options and exit
    --log2stdout
          If true, log output will be displayed to the console. Default is false.
    --log-level <value>
          Level for logging: "ERROR", "WARN", "DEBUG", or "INFO". Default is "ERROR"
    --debug
          Same as --log-level DEBUG
    --quiet
          Same as --log-level ERROR
    --verbose
          Same as --log-level INFO
    --log-file <value>
          File for log output. Default is "."
    --logback <value>
          Override all logger config with the given logback.xml file.

All of the above flags are intended for reuse in all of the SMRT services but may also be helpful for any code that
wants to use our Scala logging conventions.

Extend the `LoggerConfig` trait and have the `OptionsParser` instance invoke the `LoggerOption.add` method.:

  // 1 of 2: extend `LoggerConfig` in the Config-style class
  case class GetStatusConfig(host: String = "http://localhost",
                             port: Int = 8070) extends LoggerConfig
  
  
  // 2 of 2: make an `OptionParser` that invokes `LoggerOptions.add`
    lazy val parser = new OptionParser[GetStatusConfig]("get-status") {
      head("Get SMRTLink status ", VERSION)
      note("Tool to check the status of a currently running smrtlink server")
  
      opt[String]("host") action { (x, c) =>
        c.copy(host = x)
      } text "Hostname of smrtlink server"
  
      opt[Int]("port") action { (x, c) =>
        c.copy(port = x)
      } text "Services port on smrtlink server"
      ...
      // reuse the common `--debug` param and logging params
      LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
    }
```

Alternatively, use the `LoggerOptions.parse` and related methods to directly parse an arg array.

  object MyCode extends App {
    // whatever pre-logging config logic ...
  
    // parse the args for logging related options
    LoggerOptions.parseAddDebug(args)
  
    // whatever post-logging conig logic
  }

Command-Line Example
--------------------

There are a few practical use cases that are supported. By default, INFO level events are logged to STDOUT.

### Running a Production Server

You'll likely want to capture everything above warnings in a production environment. Use the `--log-level` and `--log-file` flags::

  $ ./smrt-server-link/target/pack/bin/pbservice status --host smrtlink-bihourly --port 8081 --log-file /var/log/my_log.log --log-level WARN

In this case, the normal params exist and only these two alter the logging.

- `--log-file` =  Where the logger will save data.
- `--log-level` = Sets the logger handler to display all WARN level messages and worse.

Users will almost always have a custom log location. Allowing this to be specified via command-line is a simple way to
support this versus requiring a custom log config file or property file.

### Dev Logging

When working on the code you probably always want to see errors. If that is true, run with `--log2stdout` and
`--log-level ERROR`::

  $ ./smrt-server-link/target/pack/bin/some_service --log2stdout --log-level ERROR

Here is the more verbose, show me all log messages example::

  $ ./smrt-server-link/target/pack/bin/some_service --log2stdout --log-level DEBUG

### Using a logback.xml config

It is possible to ignore all of the default conventions used by this API and rely on a standard logback.xml config file
via the `--logback`. This provides the most flexibility possible and relies on a well known, commonly used logging library::

  ./smrt-server-link/target/pack/bin/some_service --logback logback.xml

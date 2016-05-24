# Logging

Simple logging for two common use cases in the PacBio tools. See [smrtflow#42](https://github.com/PacificBiosciences/smrtflow/pull/42) for history.

```bash
$ ./smrt-server-tools/target/pack/bin/get-smrt-server-status -h
...
  --debug
        If true, log output will be displayed to the console. Default is false.
  --loglevel <value>
        Level for logging: "ERROR", "WARN", "DEBUG", or "INFO". Default is "ERROR"
  --logfile <value>
        File for log output. Default is "."
  --logback <value>
        Override all logger config with the given logback.xml file.
```

## Usage

All of the above flags are intended for reuse in all of the SMRT services but may also be helpful for any code that
wants to use our Scala logging conventions.

Extend the `LoggerConfig` trait and have the `OptionsParser` instance invoke the `LoggerOption.add` method.

```scala
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

## Command-Line Example

There are a few practical use cases that are supported. By default, logging information is not displayed.

### Running a Production Server

You'll likely want to capture everything above warnings in a production environment. Use the `--loglevel` and `--logfile` flags.

```bash
./smrt-server-tools/target/pack/bin/get-smrt-server-status --host smrtlink-bihourly --port 8081 --logfile /var/log/my_log.log --loglevel WARN
```

In this case, the normal params exist and only these two alter the logging.

- `--logfile` =  Where the logger will save data.
- `--loglevel` = Sets the logger handler to display all WARN level messages and worse.

Users will almost always have a custom log location. Allowing this to be specified via command-line is a simple way to
support this versus requiring a custom log config file or property file.

### Dev Logging

When working on the code you probably always want to see errors. If that is true, run with `--debug` and
`--loglevel ERROR`

```bash
./smrt-server-tools/target/pack/bin/some_service --debug --loglevel ERROR
```

Here is the more verbose, show me all log messages example.

```bash
./smrt-server-tools/target/pack/bin/some_service --debug --loglevel DEBUG
```

### Using a logback.xml config

It is possible to ignore all of the default conventions used by this API and rely on a standard logback.xml config file
via the `--logback`. This provides the most flexibility possible and relies on a well known, commonly used logging
library.

```bash
./smrt-server-tools/target/pack/bin/some_service --logback logback.xml
```
# LIMS and Resolution Service

Lab Information Managment System (LIMS) is based on [this spec](specification.md) and provides tracking and resolution service for common name or shorthand identifiers. See [smrtflow#89](https://github.com/PacificBiosciences/smrtflow/issues/89) for history.

Run the service via sbt.

```
# run the LIMS services independent of the greater codebase
sbt clean compile smrt-server-lims/run
```

You'll now have the service bound to port `8070`. The host, port and 
location of the database can all be configured in `application.conf`.

```
smrt-server-lims {
  jdbcUrl = "jdbc:h2:/tmp/stress_test;CACHE_SIZE=100000"
  host = "0.0.0.0"
  port = 8070
}
```

### Tests

`RouteImportAndResolveSpec` is the main test that exercises the API. It
confirms that `lims.yml` files can be imported via POST and resolved 
via GET for known use cases.

```
# run the integration tests
sbt smrt-server-lims/test
```

`StressTestSpec.scala` provides a test that populates an arbitrary number
of replicates then accesses the data using the exposed RESTful API. By
default it relies on an in-memory data base and small number of replicates.

Change the number of `lims.yml` imported and number of times they data
read by editing the config.

```scala
// val c = StressConfig(imports = 10, queryReps = 3) // default

// 100,000 imports at 3x read per import should take ~25s on a Mac laptop
 val c = StressConfig(imports = 100000, queryReps = 3)
```

File locations can also be used by changing the test to use
`DefaultDatabase` with `JdbcDatabase` and a file-backed `jdbcUrl`.

```scala
class StressTestSpec extends Specification
    // swap the DB here. TestDatabase is in-memory
    //with TestDatabase
    with DefaultDatabase with JdbcDatabase
    ...
  // example file-backed DB override
  override lazy val jdbcUrl = "jdbc:h2:/tmp/stress_test;CACHE_SIZE=100000"
```
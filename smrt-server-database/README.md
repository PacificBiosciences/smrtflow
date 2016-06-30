# Database (SQLite + Flyway + Slick)

See [smrtflow.readthedocs.io](http://smrtflow.readthedocs.io/) for full docs and [smrtflow](../README.md) for the base multi-project's README. 

A project for abstracting the database from our greater codebase, 
enforcing expected usage and adding support for convenient debugging and 
timing of all DB use. This all ends up being exposed as a single `RDMS`
class.

## Usage

There are three main use cases supported here: production, testing and
debugging/profiling.

The main things to know:
 
 1. Flyway is used for migrations and migrations are automatically lazy-run the first time `Database.run()` is invoked
 2. All other DB access is assumed to be via Slick and `DBIOAction` instances
 3. You must use `Database.run(DBIOAction): Future[R]`, which enforces connection pooling and other restrictions needed for SQLite
 4. Don't use nested queries. It won't reliably work on SQLite.


### Production

Use `com.pacbio.database.Database` for all access to the database. It 
is a contract that enforces use of Slick `DBIOAction` instances and
hides related Slick and Flyway machinery.

```scala
# example trait from JobDao.scala
trait SmrtLinkDalProvider extends DalProvider {
  this: SmrtLinkConfigProvider =>

  override val db: Singleton[Database] = Singleton(() => new Database(dbURI()))
}

...
# later use by JobsDao.scala in ProjectDataStore
  def getProjects(limit: Int = 100): Future[Seq[Project]] = db.run(projects.take(limit).result)
```

Alternatively, a database can be directly created with `val db = new Database(dbUri)`
where `dbUri` is the JDBC URI. e.g. `jdbc:sqlite:/my/database.db`

### Testing

In-memory access to SQLite can be used for testing with the code below.
Any test that includes this trait will have `db` accessible for use.

```scala
# example trait from JobDao.scala
trait TestDalProvider extends DalProvider {
  override val db: Singleton[Database] = Singleton(() => {
    // in-memory DB for tests
    new Database(dbURI = "jdbc:sqlite:")
  })
}
```

The most succinct form of using `Database` for in-memory testing can be
seen in the `SqlLiteAndFlywayUsageSpec.scala` test.

```scala
# makes an in-memory SQLite database for testing
val db = new TestDatabase()
```

### Debugging and Profiling 

Set the `PACBIO_DATABASE` Java system property to enable the built in
logging and profiling. This is a `-D` flag for `java` on the 
command-line. It can be set in SBT by adding this line.

```scala
javaOptions in ThisBuild += "-DPACBIO_DATABASE=profile"
```

Run the code. Verbose logging about the DB use will be displayed so pipe
it to a file.

```scala
# add debugging log level to `make start-smrt-server-analysis`
sbt "smrt-server-analysis/run --loglevel DEBUG" 2> server_error.txt > server_out.txt
```


#### Queries performed and failed
Upon JVM exit a profiling summary will be displayed in `System.out` 
(logged as INFO to the logger) and, if `/tmp` exists, two CSV files will
be made: `PACBIO_DATABASE_{usage|timing}.csv`.

```bash
# show the DB usage summary per line of scala code
cat /tmp/PACBIO_DATABASE_usage.csv 

[info] *** DB Use Summary ***
[info] Code,Success, Failures
[info] com.pacbio.secondary.smrtlink.actors.JobDataStore$class.updateJobStateByUUID(JobsDao.scala:282), 44, 11
[info] com.pacbio.secondary.smrtlink.actors.JobDataStore$class.getJobsByTypeId(JobsDao.scala:368), 36, 0
[info] com.pacbio.secondary.smrtlink.actors.JobDataStore$class.updateJobStateByUUID(JobsDao.scala:298), 33, 7
[info] com.pacbio.secondary.smrtlink.actors.JobDataStore$class.getJobByUUID(JobsDao.scala:228), 33, 4
[info] com.pacbio.secondary.smrtlink.actors.JobDataStore$class.getJobById(JobsDao.scala:231), 29, 0
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getDataSetByUUID(JobsDao.scala:645), 25, 0
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.insertDataStoreByJob(JobsDao.scala:512), 18, 6
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getReferenceDataSets(JobsDao.scala:692), 15, 0
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getSubreadDataSets(JobsDao.scala:681), 12, 0
[info] com.pacbio.secondary.smrtlink.actors.JobDataStore$class.createJob(JobsDao.scala:361), 11, 0
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getDataSetMetaDataSet(JobsDao.scala:549), 11, 2
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.insertDataStoreByJob(JobsDao.scala:500), 11, 7
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getAlignmentDataSets(JobsDao.scala:764), 9, 0
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getHdfDataSets(JobsDao.scala:718), 9, 0
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$$anonfun$insertReferenceDataSet$1.apply(JobsDao.scala:567), 6, 2
[info] com.pacbio.secondary.smrtlink.database.DatabaseRunDao.updateOrCreate(DatabaseRunDao.scala:55), 4, 0
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getReferenceDataSetById(JobsDao.scala:698), 4, 0
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$$anonfun$insertSubreadDataSet$1.apply(JobsDao.scala:587), 3, 3
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getDataStoreFiles(JobsDao.scala:822), 2, 0
[info] com.pacbio.secondary.smrtlink.actors.JobDataStore$class.getJobs(JobsDao.scala:365), 2, 0
```

Summarize errors of interest by using `grep` to pull out all cases of
the entry point that errored.

```bash
cat server_out.txt | grep -A 1 "PacBio:Database.*error.*updateJobStateByUUID(JobsDao.scala:298"

jfalkner-mac:smrtflow jfalkner$ cat debug_db.txt | grep -A 1 "PacBio:Database.*error.*updateJobStateByUUID(JobsDao.scala:282"
[info] 2016-06-13  ERROR[ForkJoinPool-2-worker-11] c.p.d.LoggingListener - [PacBio:Database]  RDMS error for com.pacbio.secondary.smrtlink.actors.JobDataStore$class.updateJobStateByUUID(JobsDao.scala:282)
[info] java.lang.RuntimeException: Can't have multiple sql connections open. An old connection may not have had close() invoked.
--
[info] 2016-06-13  ERROR[ForkJoinPool-2-worker-9] c.p.d.LoggingListener - [PacBio:Database]  RDMS error for com.pacbio.secondary.smrtlink.actors.JobDataStore$class.updateJobStateByUUID(JobsDao.scala:282)
[info] java.lang.RuntimeException: Can't have multiple sql connections open. An old connection may not have had close() invoked.
--
... All are the same error
```

#### Query timing

This tool does not replace SQL EXPLAIN use for specific slow queries. It
is a wrapper that summarizes the line of code in the Scala codebase that
ran `db.run()` and how long the entire `DBIOAction` took to execute. 
Timing summarized is approximate RDBMS time and excludes queue time.

```
# example DB use timing for `stress.py` after first imoporting large amounts of mock data
$ cat /tmp/PACBIO_DATABASE_timing.csv 

Code,Sum,Avg,Min,Max,Usage
com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getSubreadDataSets(JobsDao.scala:713), 2157, 134, 108, 188, 16
com.pacbio.secondary.smrtlink.actors.JobDataStore$class.getJobsByTypeId(JobsDao.scala:382), 1350, 32, 0, 99, 42
com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getAlignmentDataSets(JobsDao.scala:796), 1016, 101, 82, 131, 10
com.pacbio.secondary.smrtlink.actors.JobDataStore$class.getJobs(JobsDao.scala:379), 385, 128, 92, 191, 3
com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getReferenceDataSets(JobsDao.scala:724), 295, 16, 14, 21, 18
com.pacbio.secondary.smrtlink.actors.JobDataStore$class.updateJobStateByUUID(JobsDao.scala:301), 202, 3, 2, 8, 60
com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getHdfDataSets(JobsDao.scala:750), 106, 10, 9, 12, 10
com.pacbio.secondary.smrtlink.actors.JobDataStore$class.createJob(JobsDao.scala:366), 98, 6, 4, 22, 15
com.pacbio.secondary.smrtlink.actors.DataSetStore$class.addOptionalInsert$1(JobsDao.scala:535), 64, 1, 0, 3, 55
com.pacbio.secondary.smrtlink.actors.JobDataStore$class.getJobByUUID(JobsDao.scala:230), 46, 0, 0, 1, 70
com.pacbio.secondary.smrtlink.actors.JobDataStore$class.getJobById(JobsDao.scala:233), 42, 0, 0, 1, 74
com.pacbio.secondary.smrtlink.database.DatabaseRunDao.updateOrCreate(DatabaseRunDao.scala:55), 39, 7, 5, 15, 5
com.pacbio.secondary.smrtlink.actors.DataSetStore$class.insertDataStoreByJob(JobsDao.scala:539), 29, 0, 0, 2, 55
com.pacbio.secondary.smrtlink.actors.DataSetStore$class.insertSubreadDataSet(JobsDao.scala:619), 26, 5, 3, 9, 5
com.pacbio.secondary.smrtlink.actors.DataSetStore$class.insertReferenceDataSet(JobsDao.scala:599), 23, 4, 4, 5, 5
com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getDataSetByUUID(JobsDao.scala:677), 18, 0, 0, 2, 26
com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getDataStoreFiles(JobsDao.scala:854), 10, 3, 2, 5, 3
com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getReferenceDataSetById(JobsDao.scala:730), 4, 0, 0, 1, 5
com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getDataSetMetaDataSet(JobsDao.scala:577), 4, 0, 0, 1, 10
```

#### Detecting nested queries and SQLite

SQLite has a restriction of one active connection and not enforcing this
may yield occasional database lockups and timeouts or SQLITE_BUSY errors.
The `Database` class has a guard in place that'll help identify if nested
queries are being run. It is based on:

- `Database.run()` enforces that `DBIOAction` (aka queries) are queued
  and run synchronously by a single thread using a connection pool with one connection.
- `Await.result(query, 12345 milliseconds)` is used to block for the query result
- Timeouts are logged via `DatabaseListener.timeout()` before re-throw

Unless queries take more than `12.345` seconds, triggering the timeout
typically means that the query is blocked waiting on the result of
another. You'll see an exception such as the following.

```
# Example `Nested db.run() calls?` exception including showing `JobsDao.scala:505` as the offending line.
[PacBio:Database] RDMS timeout for c.p.s.s.a.DataSetStore$class.insertDataStoreByJob(JobsDao.scala:505)
[info] java.lang.Exception: Nested db.run() calls?
[info]  at com.pacbio.database.Database$$anonfun$run$2$$anonfun$apply$3$$anonfun$apply$mcV$sp$6.apply(Database.scala:200)
... 
```

The funny value of `12345` was chosen purposely because these sorts of 
timeouts may impact message delivery in Akka. If you see any `Ask timeout`
 and `Futures timed out after [12345 milliseconds]`, it helps indicate the
nested query culprit.

Verbose DEBUG logging exists of when new queries are created (aka
added to queue via `db.run()`) and execution starts and ends. A query with nested
queries will show the problematic behavior of `created` then `started` 
followed by more `created` before the original query `finished`.

```
# Example nested queries
[PacBio:Database] finished DBIOAction c.p.s.s.a.DataSetStore$class.getDataSetMetaDataSet(JobsDao.scala:554), queryCount = 0
[PacBio:Database] started DBIOAction c.p.s.s.a.DataSetStore$class.insertDataStoreByJob(JobsDao.scala:505), queryCount = 1
[PacBio:Database] created DBIOAction c.p.s.s.a.DataSetStore$$anonfun$insertReferenceDataSet$1.apply(JobsDao.scala:572)
[PacBio:Database] created DBIOAction c.p.s.s.a.JobDataStore$class.updateJobStateByUUID(JobsDao.scala:299)
[PacBio:Database] created DBIOAction c.p.s.s.a.JobDataStore$class.getJobById(JobsDao.scala:231)
```
Interestingly, in the above example the code `insertDataStoreByJob(JobsDao.scala:505)` is the entry point 
of the entire logical DB update; however, the log line above it `getDataSetMetaDataSet(JobsDao.scala:554)`
(usually) executes first because of the functional style the code is written in.

The expected output is `created`, `started` then `finished` all in 
series. It is fine to have all of these in a transaction -- it doesn't 
matter that they are re-ordered.

```
# Needed sub-query (1 of 4) that can be done ahead of time.
[PacBio:Database] created DBIOAction c.p.s.s.a.DataSetStore$class.getDataSetMetaDataSet(JobsDao.scala:554), queryCount = 0
[PacBio:Database] started DBIOAction c.p.s.s.a.DataSetStore$class.getDataSetMetaDataSet(JobsDao.scala:554), queryCount = 0
[PacBio:Database] finished DBIOAction c.p.s.s.a.DataSetStore$class.getDataSetMetaDataSet(JobsDao.scala:554), queryCount = 0

# Needed sub-query (2 of 4) that can be done ahead of time.
[PacBio:Database] created DBIOAction c.p.s.s.a.JobDataStore$class.getJobById(JobsDao.scala:231)
[PacBio:Database] started DBIOAction c.p.s.s.a.JobDataStore$class.getJobById(JobsDao.scala:231)
[PacBio:Database] finished DBIOAction c.p.s.s.a.JobDataStore$class.getJobById(JobsDao.scala:231)

# Needed sub-query (3 of 4) that can be done ahead of time.
[PacBio:Database] created DBIOAction c.p.s.s.a.DataSetStore$$anonfun$insertReferenceDataSet$1.apply(JobsDao.scala:572)
[PacBio:Database] started DBIOAction c.p.s.s.a.DataSetStore$$anonfun$insertReferenceDataSet$1.apply(JobsDao.scala:572)
[PacBio:Database] finished DBIOAction c.p.s.s.a.DataSetStore$$anonfun$insertReferenceDataSet$1.apply(JobsDao.scala:572)

# Needed sub-query (4 of 4) that can be done ahead of time.
[PacBio:Database] created DBIOAction c.p.s.s.a.JobDataStore$class.updateJobStateByUUID(JobsDao.scala:299)
[PacBio:Database] started DBIOAction c.p.s.s.a.JobDataStore$class.updateJobStateByUUID(JobsDao.scala:299)
[PacBio:Database] finished DBIOAction c.p.s.s.a.JobDataStore$class.updateJobStateByUUID(JobsDao.scala:299)

# After all sub-queries needed are done. Run the main insert.
[PacBio:Database] created DBIOAction c.p.s.s.a.DataSetStore$class.insertDataStoreByJob(JobsDao.scala:505), queryCount = 1
[PacBio:Database] started DBIOAction c.p.s.s.a.DataSetStore$class.insertDataStoreByJob(JobsDao.scala:505), queryCount = 1
[PacBio:Database] finished DBIOAction c.p.s.s.a.DataSetStore$class.insertDataStoreByJob(JobsDao.scala:505), queryCount = 1
```

If a database is used that supports multiple connections (i.e. almost
anything other than SQLite), then nesting queries is likely fine. It'd
be an extreme case that somehow saturates a connection pool with many
connections. The pool would probably be unbounded, rendering this concern
moot.

One last tactic. By default Spray/Akka will have many threads working
through actors message queues. If so, then the query logging may not be
as simple to read as it could. Restrict the thread pool to one thread
to help make the logs as straight-forward to follow as shown above.

```
# Example of restricting Akka workers to a single thread
akka {
    loglevel = DEBUG
    loggers = ["akka.event.slf4j.Slf4jLogger"]
    ...
    actor {
        ...
        default-dispatcher {
          throughput = 100000
          # Force a single thread to handle the Akka system. Breaks
          # default parralelism but clarifies annoying SQLite issues
          # where embedded queries spawn on a diff thread.
          executor = "thread-pool-executor"
          thread-pool-executor {
            # Min number of threads to cap factor-based parallelism number to
            parallelism-min = 1
            # Parallelism (threads) ... ceil(available processors * factor)
            parallelism-factor = 1
            # Max number of threads to cap factor-based parallelism number to
            parallelism-max = 1
          }
        }
    }
}
```
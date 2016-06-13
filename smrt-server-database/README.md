# Database (SQLite + Flyway + Slick)

See [smrtflow.readthedocs.io](http://smrtflow.readthedocs.io/) for full docs and [smrtflow](../README.md) for the base multi-project's README. 

A project for abstracting the database from our greater codebase, 
enforcing expected usage and adding support for convenient debugging and 
timing of all DB use. This all ends up being exposed as a single `RDMS`
class.

## Usage

There are three main use cases supported here: production, testing and
debugging/profiling.


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

> WIP: Kind of hacky. Will try to clean this up.

Run the code 

```scala
# add debugging log level to `make start-smrt-server-analysis`
sbt "smrt-server-analysis/run --loglevel DEBUG" > debug_db.txt
```

Pull the latest profiling summary to see invoked SQL, sorted by usage.
Counts for successfully completed and failed queries is provided.

```bash
# pull the profiling dump from the known header
cat debug_db.txt | grep -A 18 "DB Use Summary" | tail -n 19

[info] *** DB Use Summary ***
[info] Code,Success, Failures
[info] 2016-06-13 14:40:31.947UTC INFO [smrtlink-analysis-server-akka.actor.default-dispatcher-9] c.p.s.a.e.a.EngineDaoActor - Unhandled engine DAO message. 'Failure(java.lang.RuntimeException: Can't have multiple sql connections open. An old connection may not have had close() invoked.)'
[info] com.pacbio.secondary.smrtlink.actors.JobDataStore$class.updateJobStateByUUID(JobsDao.scala:282), 44, 11
[info] com.pacbio.secondary.smrtlink.actors.JobDataStore$class.getJobsByTypeId(JobsDao.scala:368), 36, 0
[info] com.pacbio.secondary.smrtlink.actors.JobDataStore$class.updateJobStateByUUID(JobsDao.scala:298), 33, 25
[info] com.pacbio.secondary.smrtlink.actors.JobDataStore$class.getJobByUUID(JobsDao.scala:228), 33, 0
[info] com.pacbio.secondary.smrtlink.actors.JobDataStore$class.getJobById(JobsDao.scala:231), 28, 0
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getDataSetByUUID(JobsDao.scala:645), 25, 0
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.insertDataStoreByJob(JobsDao.scala:512), 22, 12
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getReferenceDataSets(JobsDao.scala:692), 15, 0
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getSubreadDataSets(JobsDao.scala:681), 13, 0
[info] com.pacbio.secondary.smrtlink.actors.JobDataStore$class.createJob(JobsDao.scala:361), 11, 0
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getDataSetMetaDataSet(JobsDao.scala:549), 10, 0
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.insertDataStoreByJob(JobsDao.scala:500), 10, 6
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getAlignmentDataSets(JobsDao.scala:764), 9, 0
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$class.getHdfDataSets(JobsDao.scala:718), 9, 0
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$$anonfun$insertReferenceDataSet$1.apply(JobsDao.scala:567), 6, 2
[info] com.pacbio.secondary.smrtlink.actors.DataSetStore$$anonfun$insertSubreadDataSet$1.apply(JobsDao.scala:587), 4, 3
```

Summarize errors of interest by using `grep` to pull out all cases of
the entry point that errored.

```bash
cat debug_db.txt | grep -A 1 "PacBio:Database.*error.*updateJobStateByUUID(JobsDao.scala:298"

[info] 2016-06-13  ERROR[ForkJoinPool-2-worker-15] c.p.d.LoggingListener - [PacBio:Database]  RDMS error for com.pacbio.secondary.smrtlink.actors.JobDataStore$class.updateJobStateByUUID(JobsDao.scala:298)
[info] java.sql.SQLException: Connection is null.
--
[info] 2016-06-13  ERROR[ForkJoinPool-2-worker-3] c.p.d.LoggingListener - [PacBio:Database]  RDMS error for com.pacbio.secondary.smrtlink.actors.JobDataStore$class.updateJobStateByUUID(JobsDao.scala:298)
[info] java.sql.SQLException: Connection is null.
--
[info] 2016-06-13  ERROR[ForkJoinPool-2-worker-1] c.p.d.LoggingListener - [PacBio:Database]  RDMS error for com.pacbio.secondary.smrtlink.actors.JobDataStore$class.updateJobStateByUUID(JobsDao.scala:298)
[info] java.sql.SQLException: Connection is null.
...
```

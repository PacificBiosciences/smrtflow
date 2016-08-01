package com.pacbio.secondary.lims.database.h2

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}
import java.util.UUID

import com.pacbio.secondary.lims.LimsSubreadSet
import com.pacbio.secondary.lims.database.{Database, JdbcDatabase}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


/**
 * H2 implementation of the backend using plain old JDBC
 *
 * The design here is that a dedicated `Connection` and `PreparedStatement` are made for each of the
 * main API endpoints. It both keeps the H2 database open and provides efficient SQL queries. See
 * the `use` helper method for the core of the logic.
 *
 * `H2CreateTables.sql` will create needed tables and extra indexes for good performance. It is safe
 * to re-run since all statements use `IF NOT EXISTS`.
 *
 * All of the xxxT named String instances are SQL templates for the `PreparedStatement` instances, and
 * they are also used as a simply way to synchronized access. `aliasSelectT` is the only template
 * with a sub-query. It is done this way to efficiently lookup aliases and avoid multiple calls to
 * the DB.
 */
trait H2Database extends Database {
  this: JdbcDatabase =>

  private val lsFields = Seq[String](
    "uuid",
    "expid",
    "runcode",
    "path",
    "pa_version",
    "ics_version",
    "well",
    "context",
    "created_at",
    "inst_name",
    "instid")

  // serves as PreparedStatement template and lock for dedicated connection
  private val lsMergeT = s"MERGE INTO LIMS_SUBREADSET (${lsFields.mkString(",")}) VALUES (${lsFields.map(_ => "?").mkString(",")});"
  private val lsSelectT = "SELECT * FROM LIMS_SUBREADSET WHERE uuid = ?;"
  private val aliasMergeT = "MERGE INTO ALIAS (alias, uuid, type) VALUES (?, ?, ?)"
  private val aliasSelectT = "SELECT * FROM LIMS_SUBREADSET WHERE uuid = (SELECT UUID FROM ALIAS WHERE ALIAS = ?);"
  private val aliasDeleteT = "DELETE FROM ALIAS WHERE alias = ?;"
  private val expSelectT = "SELECT * FROM LIMS_SUBREADSET WHERE expid = ?;"
  private val rcSelectT = "SELECT * FROM LIMS_SUBREADSET WHERE runcode = ?;"
  private val psCache = new mutable.HashMap[String, PreparedStatement]()

  // JDBC driver has to be loaded before DriveManager.getConnection is used
  Class.forName("org.h2.Driver")

  def getConnection(): Connection = DriverManager.getConnection(jdbcUrl)

  // helper method to lazy make, recover and use the cached PreparedStatements
  private def use[T](template: String, f: PreparedStatement => T): T = {
    template.synchronized {
      // get prepared statement, make sure it is not closed and use cache
      val lm = psCache.get(template) match {
        case Some(x) => if (x.getConnection.isClosed) getConnection().prepareStatement(template) else x
        case _ => {
          val ps = getConnection().prepareStatement(template)
          psCache.put(template, ps)
          ps
        }
      }
      // run the query and return the results
      f(lm)
    }
  }

  def doSetSubread(l: LimsSubreadSet)(ps: PreparedStatement): String = {
    for ((x, i) <- LimsSubreadSet.unapply(l).get.productIterator.toList.view.zipWithIndex)
      x match {
        case x: String => ps.setString(i+1, x)
        case x: UUID => ps.setString(i+1, x.toString)
        case x: Int => ps.setInt(i+1, x)
      }
    if (ps.executeUpdate() == 1) s"Merged: $l" else throw new Exception(s"Couldn't merge: $l")
  }

  override def setSubread(l: LimsSubreadSet): String = {
    use[String](lsMergeT, doSetSubread(l))
  }

  def doAliasMerge(a: String, uuid: UUID, typ: String)(ps: PreparedStatement) : String = {
    ps.setString(1, a)
    ps.setString(2, uuid.toString)
    ps.setString(3, typ)
    if (ps.executeUpdate() == 1) s"Merged: $a" else throw new Exception(s"Couldn't merge: $a, $uuid")
  }

  override def setAlias(a: String, uuid: UUID, typ: String): Unit = use[String](aliasMergeT, doAliasMerge(a, uuid, typ))

  private def doRunCode(v: String)(ps: PreparedStatement) : Seq[LimsSubreadSet] = {
    ps.setString(1, v)
    subreads(ps.executeQuery())
  }

  override def subreadsByRunCode(rc: String): Seq[LimsSubreadSet] = use[Seq[LimsSubreadSet]](rcSelectT, doRunCode(rc))

  private def doExperiment(v: Int)(ps: PreparedStatement) : Seq[LimsSubreadSet] = {
    ps.setInt(1, v)
    subreads(ps.executeQuery())
  }

  override def subreadsByExperiment(q: Int): Seq[LimsSubreadSet] = use[Seq[LimsSubreadSet]](expSelectT, doExperiment(q))

  private def doAlias(v: String)(ps: PreparedStatement): LimsSubreadSet = {
    ps.setString(1, v)
    subreads(ps.executeQuery()).head
  }

  override def subreadByAlias(q: String): LimsSubreadSet = use[LimsSubreadSet](aliasSelectT, doAlias(q))

  private def doLims(uuid: UUID)(ps: PreparedStatement): Option[LimsSubreadSet] = {
    println("Executing: doLims()")
    ps.setString(1, uuid.toString)
    subreads(ps.executeQuery()).headOption
  }

  override def subread(uuid: UUID): Option[LimsSubreadSet] = use[Option[LimsSubreadSet]](lsSelectT, doLims(uuid))

  override def subreads(uuids: Seq[UUID]): Seq[LimsSubreadSet] = (for (uuid <- uuids) yield subread(uuid)).flatMap(sr => sr)

  private def doAliasDel(v: String)(ps: PreparedStatement) : Unit = {
    ps.setString(1, v)
    ps.executeQuery()
  }

  override def delAlias(q: String): Unit = use[Unit](aliasDeleteT, doAliasDel(q))

  // helper to convert ResultSet rows to LimsYml
  private def subreads(rs: ResultSet) : Seq[LimsSubreadSet] = {
    val buf = ArrayBuffer[LimsSubreadSet]()
    while (rs.next()) buf.append(
      LimsSubreadSet(
        uuid = UUID.fromString(rs.getString("uuid")),
        expid = rs.getInt("expid"),
        runcode = rs.getString("runcode"),
        path = rs.getString("path"),
        pa_version = rs.getString("pa_version"),
        ics_version = rs.getString("ics_version"),
        well = rs.getString("well"),
        context = rs.getString("context"),
        created_at = rs.getString("created_at"),
        inst_name = rs.getString("inst_name"),
        instid = rs.getInt("instid")))
    rs.close()
    buf.toList
  }

  /**
   * Creates the needed tables
   *
   * This is in place of using a migration service for the first iteration. This is an internal
   * service. Can move to a migration-based strategy later, as needed.
   */
  def createTables(): Unit = {
    val c = getConnection()
    try {
      c.setAutoCommit(false)
      val s = c.createStatement()
      try s.execute(H2TableCreate.sql) finally s.close()
    }
    finally { c.commit(); c.close() }
  }
}

object H2TableCreate {
  val sql =
    """-- embedded as a var b/c build is choking on external script.
      |CREATE TABLE IF NOT EXISTS LIMS_SUBREADSET (
      |  uuid UUID,
      |  expid INT,
      |  runcode VARCHAR,
      |  path VARCHAR,
      |  pa_version VARCHAR,
      |  ics_version VARCHAR,
      |  well VARCHAR,
      |  context VARCHAR,
      |  created_at VARCHAR,
      |  inst_name VARCHAR,
      |  instid INT,
      |  PRIMARY KEY (uuid, expid, runcode));
      |-- this exists only in this service. arbitrary aliases or short codes
      |CREATE TABLE IF NOT EXISTS ALIAS (
      |  alias VARCHAR PRIMARY KEY,
      |  uuid UUID,
      |  type VARCHAR);
      |-- two indexes to support the queries that PK indexes don't cover
      |CREATE INDEX IF NOT EXISTS index_lims_subreadset_runcode ON LIMS_SUBREADSET(RUNCODE);
      |CREATE INDEX IF NOT EXISTS index_lims_subreadset_uuid ON LIMS_SUBREADSET(expid);
      |CREATE INDEX IF NOT EXISTS index_lims_subreadset_uuid ON LIMS_SUBREADSET(UUID);
      |""".stripMargin
}
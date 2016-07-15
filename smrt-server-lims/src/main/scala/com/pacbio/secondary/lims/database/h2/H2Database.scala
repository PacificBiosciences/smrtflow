package com.pacbio.secondary.lims.database.h2

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}

import com.pacbio.secondary.lims.LimsYml
import com.pacbio.secondary.lims.database.{Database, JdbcDatabase}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source


/**
 * H2 implementation of the backend using plain old JDBC
 *
 * The design here is that a dedicated `Connection` and `PreparedStatement` are made for each of the
 * main API endpoints. It both keeps the H2 database open and provides efficient SQL queries. See
 * the `use` helper method for the core of the logic.
 *
 * `create_tables.sql` will create needed tables and extra indexes for good performance. It is safe
 * to re-run since all statements use `IF NOT EXISTS`.
 *
 * All of the xxxT named String instances are SQL templates for the `PreparedStatement` instances, and
 * they are also used as a simply way to synchronized access. `aliasSelectT` is the only template
 * with a sub-query. It is done this way to efficiently lookup aliases and avoid multiple calls to
 * the DB.
 */
trait H2Database extends Database {
  this: JdbcDatabase =>

  // some common queries
  private val limsFields = Seq[String](
    "expcode",
    "runcode",
    "path",
    "user",
    "uid",
    "tracefile",
    "description",
    "wellname",
    "cellbarcode",
    "seqkitbarcode",
    "cellindex",
    "colnum",
    "samplename",
    "instid")
  // serves as PreparedStatement template and lock for dedicated connection
  private val lyMergeT = s"MERGE INTO LIMS_YML (${limsFields.mkString(",")}) VALUES (${limsFields.map(_ => "?").mkString(",")});"
  private val lySelectT = "SELECT * FROM LIMS_YML WHERE expcode = ? AND runcode = ?;"
  private val aliasMergeT = "MERGE INTO ALIAS (alias, lims_yml_uuid) VALUES (?, ?)"
  private val aliasSelectT = "SELECT * FROM LIMS_YML WHERE runcode = (SELECT LIMS_YML_UUID FROM ALIAS WHERE ALIAS = ?);"
  private val aliasDeleteT = "DELETE FROM ALIAS WHERE alias = ?;"
  private val expSelectT = "SELECT * FROM LIMS_YML WHERE expcode = ?;"
  private val rcSelectT = "SELECT * FROM LIMS_YML WHERE runcode = ?;"
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

  def doSetLimsYml(v: LimsYml)(ps: PreparedStatement) : String = {
    for ((x, i) <- LimsYml.unapply(v).get.productIterator.toList.view.zipWithIndex)
      x match {
        case x: String => ps.setString(i+1, x)
        case x: Int => ps.setInt(i+1, x)
      }
    if (ps.executeUpdate() == 1) "Merged: $v" else throw new Exception("Couldn't merge: $v")
  }

  override def setLimsYml(v: LimsYml): String = use[String](lyMergeT, doSetLimsYml(v))

  def doAliasMerge(a: String, pk: String)(ps: PreparedStatement) : String = {
    ps.setString(1, a)
    ps.setString(2, pk)
    if (ps.executeUpdate() == 1) "Merged: $a" else throw new Exception(s"Couldn't merge: $a, $pk")
  }

  override def setAlias(a: String, pk: String): Unit = use[String](aliasMergeT, doAliasMerge(a, pk))

  private def doRunCode(v: String)(ps: PreparedStatement) : Seq[LimsYml] = {
    ps.setString(1, v)
    ly(ps.executeQuery())
  }

  override def getByRunCode(rc: String): Seq[LimsYml] = use[Seq[LimsYml]](rcSelectT, doRunCode(rc))

  private def doExperiment(v: Int)(ps: PreparedStatement) : Seq[LimsYml] = {
    ps.setInt(1, v)
    ly(ps.executeQuery())
  }

  override def getByExperiment(q: Int): Seq[LimsYml] = use[Seq[LimsYml]](expSelectT, doExperiment(q))

  private def doAlias(v: String)(ps: PreparedStatement): LimsYml = {
    ps.setString(1, v)
    ly(ps.executeQuery()).head
  }

  override def getByAlias(q: String): LimsYml = use[LimsYml](aliasSelectT, doAlias(q))

  private def doLimsYml(pk: (Int, String))(ps: PreparedStatement): LimsYml = {
    ps.setInt(1, pk._1)
    ps.setString(2, pk._2)
    ly(ps.executeQuery()).head
  }

  override def getLimsYml(pk: (Int, String)): LimsYml = use[LimsYml](lySelectT, doLimsYml(pk))

  override def getLimsYml(pks: Seq[(Int, String)]): Seq[LimsYml] = for (pk <- pks) yield getLimsYml(pk)

  private def doAliasDel(v: String)(ps: PreparedStatement) : Unit = {
    ps.setString(1, v)
    ps.executeQuery()
  }

  override def delAlias(q: String): Unit = use[Unit](aliasDeleteT, doAliasDel(q))

  // helper to convert ResultSet rows to LimsYml
  private def ly(rs: ResultSet) : Seq[LimsYml] = {
    val buf = ArrayBuffer[LimsYml]()
    while (rs.next()) buf.append(
    LimsYml(
      expcode = rs.getInt("expcode"),
      runcode = rs.getString("runcode"),
      path = rs.getString("path"),
      user = rs.getString("user"),
      uid = rs.getString("uid"),
      tracefile = rs.getString("tracefile"),
      description = rs.getString("description"),
      wellname = rs.getString("wellname"),
      cellbarcode = rs.getString("cellbarcode"),
      cellindex = rs.getInt("cellindex"),
      seqkitbarcode = rs.getString("seqkitbarcode"),
      colnum = rs.getInt("colnum"),
      samplename = rs.getString("samplename"),
      instid = rs.getInt("instid")
    ))
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
      val file = "/com/pacbio/secondary/lims/database/h2/create_tables.sql"
      val sql = new String(Source.fromInputStream(getClass.getResourceAsStream(file)).toArray)
      val s = c.createStatement()
      try s.execute(sql) finally s.close()
    }
    finally { c.commit(); c.close() }
  }
}
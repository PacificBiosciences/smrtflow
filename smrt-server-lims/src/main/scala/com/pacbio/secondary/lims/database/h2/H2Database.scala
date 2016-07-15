package com.pacbio.secondary.lims.database.h2

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}

import com.pacbio.secondary.lims.LimsYml
import com.pacbio.secondary.lims.database.{Database, JdbcDatabase}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source


/**
 * H2 implementation of the backend using plain old JDBC
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
  private val lySelectT = "SELECT * FROM LIMS_YML WHERE id = ?;"
  private val aliasMergeT = "MERGE INTO ALIAS (alias, lims_yml_id) VALUES (?, ?)"
  private val aliasSelectT = "SELECT lims_yml_id FROM ALIAS WHERE alias = ?;"
  private val aliasDeleteT = "DELETE FROM ALIAS WHERE alias = ?;"
  private val expSelectT = "SELECT id FROM LIMS_YML WHERE expcode = ?;"
  private val rcSelectT = "SELECT id FROM LIMS_YML WHERE runcode = ?;"
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

  def doAliasMerge(a: String, id: Int)(ps: PreparedStatement) : String = {
    ps.setString(1, a)
    ps.setInt(2, id)
    if (ps.executeUpdate() == 1) "Merged: $a" else throw new Exception(s"Couldn't merge: $a, $id")
  }

  override def setAlias(a: String, id: Int): Unit = use[String](aliasMergeT, doAliasMerge(a, id))

  private def doRunCode(v: String)(ps: PreparedStatement) : Seq[Int] = {
    ps.setString(1, v)
    ids(ps.executeQuery())
  }

  override def getByRunCode(q: String): Seq[Int] = use[Seq[Int]](rcSelectT, doRunCode(q))

  private def doExperiment(v: Int)(ps: PreparedStatement) : Seq[Int] = {
    ps.setInt(1, v)
    ids(ps.executeQuery())
  }

  override def getByExperiment(q: Int): Seq[Int] = use[Seq[Int]](expSelectT, doExperiment(q))

  private def doAlias(v: String)(ps: PreparedStatement): Int = {
    ps.setString(1, v)
    ids(ps.executeQuery()).head
  }

  override def getByAlias(q: String): Int = use[Int](aliasSelectT, doAlias(q))

  private def doLimsYml(id: Int)(ps: PreparedStatement): LimsYml = {
    ps.setInt(1, id)
    ly(ps.executeQuery())
  }

  override def getLimsYml(q: Int): LimsYml = use[LimsYml](lySelectT, doLimsYml(q))

  override def getLimsYml(q: Seq[Int]): Seq[LimsYml] = for (id <- q) yield getLimsYml(id)

  private def doAliasDel(v: String)(ps: PreparedStatement) : Unit = {
    ps.setString(1, v)
    ps.executeQuery()
  }

  override def delAlias(q: String): Unit = use[Unit](aliasDeleteT, doAliasDel(q))

  // helper to convert ResultSet rows to LimsYml
  private def ly(rs: ResultSet) : LimsYml = {
    rs.next
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
    )
  }

  // helper to convert ResultSet to Int list
  def ids(rs: ResultSet): Seq[Int] = {
    // weird, same issue? -- http://stackoverflow.com/questions/4380831/why-does-filter-have-to-be-defined-for-pattern-matching-in-a-for-loop-in-scala
    //val all = for {id <- rs.getInt(1) if rs.next()} yield id
    val buf = ArrayBuffer[Int]()
    while (rs.next()) buf.append(rs.getInt(1))
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

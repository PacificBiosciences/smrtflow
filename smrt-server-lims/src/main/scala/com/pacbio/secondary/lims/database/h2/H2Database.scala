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
  this: JdbcDatabase => // (jdbcUrl: String = "jdbc:h2:./lims;DB_CLOSE_DELAY=3")

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
  private val importTemplate = s"MERGE INTO LIMS_YML (${limsFields.mkString(",")}) VALUES (${limsFields.map(_ => "?").mkString(",")});"
  private val expTemplate = "SELECT id FROM LIMS_YML WHERE expcode = ?;"
  private val runCodeTemplate = "SELECT id FROM LIMS_YML WHERE runcode = ?;"
  private val aliasTemplate = "SELECT lims_yml_id FROM ALIAS WHERE alias = ?;"
  private val delAliasTemplate = "DELETE FROM ALIAS WHERE alias = ?;"
  private val lyTemplate = "SELECT * FROM LIMS_YML WHERE id = ?;"
  private val lockMap = new mutable.HashMap[String, PreparedStatement]()

  // init the H2 connection
  Class.forName("org.h2.Driver")

  def getConnection(): Connection = {
    DriverManager.getConnection(jdbcUrl)
  }

  /**
   * Creates the lims.yml record, if it doesn't already exist
   *
   * @return
   */
  var slyLock = new Object()
  var slyps: PreparedStatement = null
  override def setLimsYml(v: LimsYml): String = {
    slyLock.synchronized {
      // dedicated connection and prepared statement
      if (slyps == null) slyps = getConnection().prepareStatement(importTemplate)
      for ((x, i) <- LimsYml.unapply(v).get.productIterator.toList.view.zipWithIndex)
        x match {
          case x: String => slyps.setString(i+1, x)
          case x: Int => slyps.setInt(i+1, x)
        }
      val result = slyps.executeUpdate()
      if (result == 1) {
        "Merged: " + v
      }
      else {
        throw new Exception("Couldn't merge: " + v)
      }
    }
  }

  override def setAlias(a: String, id: Int): Unit = {
    val c = getConnection()
    try {
      val s = c.createStatement()
      try {
        val sql = s"MERGE INTO ALIAS (alias, lims_yml_id) VALUES ('$a', $id)"
        val result = s.executeUpdate(sql)
        if (result != 1) {
          throw new Exception("Couldn't merge: $a, $id")
        }
      }
      finally {
        s.close()
      }
    }
    finally {
      c.close()
    }
  }

  /**
   * Helper for the common task of going from result set to LimsYml
   */
  def ly(rs: ResultSet) : LimsYml = {
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

  def ids(rs: ResultSet): Seq[Int] = {
    // weird, same issue? -- http://stackoverflow.com/questions/4380831/why-does-filter-have-to-be-defined-for-pattern-matching-in-a-for-loop-in-scala
    //val all = for {id <- rs.getInt(1) if rs.next()} yield id
    val buf = ArrayBuffer[Int]()
    while (rs.next()) {
      buf.append(rs.getInt(1))
    }
    rs.close()
    buf.toList
  }

  private def usePreparedStatement[T](template: String, f: PreparedStatement => T): T = {
    template.synchronized {
      // get prepared statement, make sure it is not closed and use cache
      val lm = lockMap.get(template) match {
        case Some(x) => if (x.getConnection.isClosed) getConnection().prepareStatement(template) else x
        case _ => {
          val ps = getConnection().prepareStatement(template)
          lockMap.put(template, ps)
          ps
        }
      }
      // run the query and return the results
      f(lm)
    }
  }

  def doRunCode(v: String)(ps: PreparedStatement) : Seq[Int] = {
    // return the query
    ps.setString(1, v)
    ids(ps.executeQuery())
  }

  override def getByRunCode(q: String): Seq[Int] =
    usePreparedStatement[Seq[Int]](runCodeTemplate, doRunCode(q))

  def doExperiment(v: Int)(ps: PreparedStatement) : Seq[Int] = {
    ps.setInt(1, v)
    ids(ps.executeQuery())
  }

  override def getByExperiment(q: Int): Seq[Int] =
    usePreparedStatement[Seq[Int]](expTemplate, doExperiment(q))

  def doAlias(v: String)(ps: PreparedStatement) : Int = {
    ps.setString(1, v)
    ids(ps.executeQuery()).head
  }

  override def getByAlias(q: String): Int =
    usePreparedStatement[Int](aliasTemplate, doAlias(q))


  def doLimsYml(id: Int)(ps: PreparedStatement): LimsYml = {
    ps.setInt(1, id)
    ly(ps.executeQuery())
  }

  override def getLimsYml(q: Int): LimsYml =
    usePreparedStatement[LimsYml](lyTemplate, doLimsYml(q))

  override def getLimsYml(q: Seq[Int]): Seq[LimsYml] = for (id <- q) yield getLimsYml(id)

  def doDelAlias(v: String)(ps: PreparedStatement) : Unit = {
    ps.setString(1, v)
    ps.executeQuery()
  }

  override def delAlias(q: String): Unit =
    usePreparedStatement[Unit](delAliasTemplate, doDelAlias(q))

  /**
   * Throwaway lazy table creation method
   *
   * Need to move to use migrations or similar. This is here just for the first iteration.
   */
  def createTables: Unit = {
    val c = getConnection()
    try {
      c.setAutoCommit(false)
      val file = "/com/pacbio/secondary/lims/database/h2/create_tables.sql"
      val sql = new String(Source.fromInputStream(getClass.getResourceAsStream(file)).toArray)
      val s = c.createStatement()
      try {
        s.execute(sql)
      }
      finally {
        s.close()
      }
    }
    catch {
      case t => {
        println("*** lazy-load error ***")
        println(t)
      }
    }
    finally {
      c.commit()
      c.close()
    }
  }
}

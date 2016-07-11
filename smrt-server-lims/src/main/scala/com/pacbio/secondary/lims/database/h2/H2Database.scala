package com.pacbio.secondary.lims.database.h2

import java.sql.{Connection, DriverManager, ResultSet}

import com.pacbio.secondary.lims.LimsYml
import com.pacbio.secondary.lims.database.{Database, JdbcDatabase}

import scala.collection.mutable.ArrayBuffer
import scala.util.Try


/**
 * H2 implementation of the backend
 */
trait H2Database extends Database {
  this: JdbcDatabase => // (jdbcUrl: String = "jdbc:h2:./lims;DB_CLOSE_DELAY=3")

  val limsYml = "LIMS_YML"

  // init the H2 connection
  Class.forName("org.h2.Driver")

  def getConnection(): Connection = {
    lazyCreateTables
    DriverManager.getConnection(jdbcUrl)
  }

  /**
   * Creates the lims.yml record, if it doesn't already exist
   *
   * @param v
   * @return
   */
  override def setLimsYml(v: LimsYml): String = {
    val c = getConnection()
    try {
      val s = c.createStatement()
      try {
        val sql = s"""
          |MERGE INTO $limsYml
          | (id,
          |  expcode,
          |  runcode,
          |  path,
          |  user,
          |  uid,
          |  tracefile,
          |  description,
          |  wellname,
          |  cellbarcode,
          |  seqkitbarcode,
          |  cellindex,
          |  colnum,
          |  samplename,
          |  instid)
          | VALUES ( 0,
          |  ${v.expcode},
          |  ${v.runcode},
          |  ${v.path},
          |  ${v.user},
          |  ${v.uid},
          |  ${v.tracefile},
          |  ${v.description},
          |  ${v.wellname},
          |  ${v.cellbarcode},
          |  ${v.seqkitbarcode},
          |  ${v.cellindex},
          |  ${v.colnum},
          |  ${v.samplename},
          |  ${v.instid});
        """.stripMargin
        val result = s.executeUpdate(sql)
        if (result == 1) {
          "Merged: " + v
        }
        else {
          throw new Exception("Couldn't merge: " + v)
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
   *
   * @param rs
   * @return
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
    //val all = for {id <- rs.getInt("id") if rs.next()} yield id
    val buf = ArrayBuffer[Int]()
    while (rs.next()) {
      buf.append(rs.getInt(1))
    }
    rs.close()
    buf.toList
  }

  private def safeGet[T](sql: String, f: ResultSet => T): T = {
    val c = getConnection()
    try {
      val s = c.createStatement()
      try {
        val rs = s.executeQuery(sql)
        f(rs)
      }
      finally {
        s.close()
      }
    }
    finally {
      c.close()
    }
  }

  override def getByRunCode(q: String): Seq[Int] =
    safeGet[Seq[Int]](s"SELECT id FROM $limsYml WHERE runcode = '$q'", ids)

  override def getByExperiment(q: Int): Seq[Int] =
    safeGet[Seq[Int]](s"SELECT id FROM $limsYml WHERE expcode = '$q'", ids)

  override def getByUUID(q: String): Seq[Int] = List[Int]()

  override def getLimsYml(q: Seq[Int]): Seq[LimsYml] =
    for (id <- q) yield getLimsYml(id)

  override def getLimsYml(q: Int): LimsYml =
    safeGet[LimsYml](s"SELECT * FROM $limsYml WHERE id = '$q'", ly)


  /**
   * Throwaway lazy table creation method
   *
   * Need to move to use migrations or similar. This is here just for the first iteration.
   */
  var createdTables: Boolean = false
  def lazyCreateTables: Unit = {
    if (!createdTables) {
      createdTables = true
      val c = getConnection()
      try {
        c.setAutoCommit(false)

        val createLimsYml =
          s"""CREATE TABLE IF NOT EXISTS $limsYml (
              |  id IDENTITY PRIMARY KEY, -- arbitrary primary ket ID
              |  expcode INT,
              |  runcode VARCHAR,
              |  path VARCHAR,
              |  user VARCHAR,
              |  uid VARCHAR,
              |  tracefile VARCHAR,
              |  description VARCHAR,
              |  wellname VARCHAR,
              |  cellbarcode VARCHAR,
              |  seqkitbarcode VARCHAR,
              |  cellindex VARCHAR,
              |  colnum VARCHAR,
              |  samplename VARCHAR,
              |  instid INT
              |);
          """.stripMargin
        // table for arbitrary aliases
        val createAlias =
          s"""CREATE TABLE IF NOT EXISTS ALIAS (
              |  alias VARCHAR PRIMARY KEY,
              |  lims_yml_id INT
              |);
          """.stripMargin
        val s = c.createStatement()
        try {
          s.execute(createLimsYml)
          s.execute(createAlias)
        }
        finally {
          s.close()
        }
      }
      finally {
        c.commit()
        c.close()
      }
    }
  }

}

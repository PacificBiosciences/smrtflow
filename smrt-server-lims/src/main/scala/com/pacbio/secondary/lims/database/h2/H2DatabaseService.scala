package com.pacbio.secondary.lims.database.h2

import java.sql.{Connection, DriverManager, ResultSet}

import com.pacbio.secondary.lims.LimsYml
import com.pacbio.secondary.lims.database.{DatabaseService, JdbcDatabaseService}


object H2DatabaseService {
  val limsYmlTable = "LIMS_YML"
}

/**
 * H2 implementation of the backend
 */
trait H2DatabaseService extends DatabaseService {
  this: JdbcDatabaseService => // (jdbcUrl: String = "jdbc:h2:./lims;DB_CLOSE_DELAY=3")

  // init the H2 connection
  Class.forName("org.h2.Driver")
  // create/migration hook

  def getConnection(): Connection = {
    lazyCreateTables
    println("Returning Connection: "+jdbcUrl)
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
          |MERGE INTO ${H2DatabaseService.limsYmlTable}
          | (expcode,
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
          | VALUES (
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
  def limsYmlFromResultSet(rs: ResultSet) : LimsYml = {
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

  def uuid(rs: ResultSet): String = {
    rs.getString("uid")
  }


  /**
   * Safely executes a query expecting one LimsYml
   *
   * @param q
   * @return
   */
  private def safeGet[T](sql: String, f: ResultSet => T): T = {
    val c = getConnection()
    try {
      val s = c.createStatement()
      try {
        val rs = s.executeQuery(sql)
        // return just the first match
        if (rs.next()) {
          val toreturn = f(rs)
          if (!rs.next()) {
            toreturn
          }
          else {
            // if multiple matches, error. expected just one
            throw new Exception("Too many results")
          }
        }
        else {
          throw new Exception("No results")
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

  // starts with or exact match
  override def getByUUID(q: String): String = {
    safeGet[String](s"SELECT uid FROM ${H2DatabaseService.limsYmlTable} WHERE uid = '$q'", uuid)
  }

  override def getByUUIDPrefix(q: String): String = {
    safeGet[String](s"SELECT uid FROM ${H2DatabaseService.limsYmlTable} WHERE uid LIKE '$q%'", uuid)
  }

  /**
   * Throwaway table creation method
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
        val sql =
          s"""CREATE TABLE IF NOT EXISTS ${H2DatabaseService.limsYmlTable} (
              |  expcode INT,
              |  runcode VARCHAR,
              |  path VARCHAR,
              |  user VARCHAR,
              |  uid VARCHAR PRIMARY KEY,
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
        val s = c.prepareStatement(sql)
        try {
          s.executeUpdate()
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

package com.pacbio.common.database

import java.io.File

import com.google.common.annotations.VisibleForTesting
import slick.driver.SQLiteDriver.api._

import scala.collection.mutable

object DatabaseProvider {
  val DRIVER = "org.sqlite.JDBC"

  // -1 queueSize means unlimited. This probably needs to be tuned
  val EXECUTOR = AsyncExecutor("db-executor", 1, -1)
}

trait DatabaseProvider {
  import DatabaseProvider._

  protected def fullURI(s: String): String = if (s.startsWith("jdbc:sqlite:")) s else s"jdbc:sqlite:$s"

  private val dbRefs: mutable.Map[String, Database] = new mutable.HashMap
  private val uris: mutable.Map[String, String] = new mutable.HashMap

  /**
   * Creates a new database, using the full URI derived from the given base URI. If a database instance has already been
   * created using that full URI, it will be returned instead.
   */
  def db(baseURI: String): Database = {
    val uri = fullURI(baseURI)
    if (dbRefs contains uri) dbRefs(uri) else {
      val db = Database.forURL(uri, driver = DRIVER, executor = EXECUTOR)
      dbRefs(uri) = db
      uris(baseURI) = uri
      db
    }
  }

  /**
   * Returns the full URI corresponding to the given base URI, if a database instance has been created using that base
   * URI.
   */
  @VisibleForTesting
  def getFullURI(baseURI: String): String = uris(baseURI)
}

@VisibleForTesting
trait TestDatabaseProvider extends DatabaseProvider {
  // Ignore provided uri, and create tmp file instead
  override protected def fullURI(baseURI: String) = {
    val dbFile = File.createTempFile("db/test_db_", ".db")
    dbFile.deleteOnExit()
    s"jdbc:sqlite:${dbFile.getCanonicalPath}?cache=shared"
  }
}

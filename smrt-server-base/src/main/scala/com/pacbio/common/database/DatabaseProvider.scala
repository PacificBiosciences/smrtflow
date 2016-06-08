package com.pacbio.common.database

import java.io.File

import com.google.common.annotations.VisibleForTesting
import com.pacbio.common.dependency.Singleton
import slick.driver.SQLiteDriver.api._

object DatabaseProvider {
  val DRIVER = "org.sqlite.JDBC"

  // -1 queueSize means unlimited. This probably needs to be tuned
  val EXECUTOR = AsyncExecutor("db-executor", 1, -1)
}

trait DatabaseProvider {
  import DatabaseProvider._

  private def toURI(s: String) = if (s.startsWith("jdbc:sqlite:")) s else s"jdbc:sqlite:$s"

  val dbURI: Singleton[String]
  val db: Singleton[Database] = Singleton(() => Database.forURL(toURI(dbURI()), driver = DRIVER, executor = EXECUTOR))
}

@VisibleForTesting
trait TestDatabaseProvider extends DatabaseProvider {
  import DatabaseProvider._

  private val uri = {
    val dbFile = File.createTempFile("test_dal_", ".db")
    dbFile.deleteOnExit()
    s"jdbc:sqlite:file:${dbFile.getCanonicalPath}?cache=shared"
  }

  override val dbURI: Singleton[String] = Singleton(uri)
  override val db: Singleton[Database] = Singleton(() => Database.forURL(uri, driver = DRIVER, executor = EXECUTOR))
}

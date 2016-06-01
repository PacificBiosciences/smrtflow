package com.pacbio.common.database

import com.pacbio.common.dependency.Singleton
import slick.driver.SQLiteDriver.api._

trait DatabaseProvider {
  val dbURI: Singleton[String]
  val db: Singleton[Database] = Singleton(() => Database.forURL(dbURI(), driver = "org.sqlite.JDBC"))
}

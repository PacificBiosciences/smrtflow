package com.pacbio.secondary.smrtlink.database

import java.nio.file.Paths

import com.pacbio.common.database.DatabaseProvider
import com.pacbio.common.dependency.{Singleton, InitializationComposer, RequiresInitialization}
import org.flywaydb.core.Flyway

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

class FlywayMigrator(dbURI: String) extends RequiresInitialization {
  val flyway = new Flyway() {
    override def migrate(): Int = {
      // lazy make directories as needed for sqlite
      if (dbURI.startsWith("jdbc:sqlite:")) {
        val file = Paths.get(dbURI.stripPrefix("jdbc:sqlite:"))
        if (file.getParent != null) {
          val dir = file.getParent.toFile
          if (!dir.exists()) dir.mkdirs()
        }
      }

      super.migrate()
    }
  }

  override def init(): Future[Int] = Future {
    flyway.setDataSource(dbURI, "", "")
    flyway.setBaselineOnMigrate(true)
    flyway.setBaselineVersionAsString("1")
    flyway.migrate()
  }
}

trait FlywayMigratorProvider {
  this: DatabaseProvider with InitializationComposer =>

  val flywayMigrator: Singleton[FlywayMigrator] =
    requireInitialization(Singleton(() => new FlywayMigrator(dbURI())))
}

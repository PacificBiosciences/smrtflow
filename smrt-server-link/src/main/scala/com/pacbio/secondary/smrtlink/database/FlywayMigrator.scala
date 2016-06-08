package com.pacbio.secondary.smrtlink.database

import java.nio.file.Paths

import com.pacbio.common.dependency.{Singleton, InitializationComposer, RequiresInitialization}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import org.flywaydb.core.Flyway

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

  override def init(): Int = {
    flyway.setDataSource(dbURI, "", "")
    flyway.setBaselineOnMigrate(true)
    flyway.setBaselineVersionAsString("1")
    flyway.migrate()
  }
}

trait FlywayMigratorProvider {
  this: SmrtLinkConfigProvider with InitializationComposer =>

  val flywayMigrator: Singleton[FlywayMigrator] =
    requireInitialization(Singleton(() => new FlywayMigrator(dbURI())))
}

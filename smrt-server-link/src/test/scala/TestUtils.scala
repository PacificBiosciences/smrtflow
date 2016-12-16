import java.nio.file.{Files, Path}

import com.pacbio.secondary.smrtlink.database.{DatabaseConfig, DatabaseUtils}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


/**
  * Testing Utils for Creating and dropping resources (e.g., db, job-root)
  *
  */
trait TestUtils extends DatabaseUtils with LazyLogging{

  /**
    * Drop the SMRT Link tables and flyway version_schema table
    * and recreate and run migrations.
    *
    * @param config Database configuration
    */
  def setupDb(config: DatabaseConfig): Unit = {
    logger.info(s"Attempting setting up db $config")
    val db = config.toDatabase
    val defaultTimeOut = 10.seconds

    val runner = for {
      m1 <- dropTables(db)
      m2 <- dropFlywayTable(db)
      m3 <- Future { Migrator(config.toDataSource) }.map(n => s"$n migrations applied")
    } yield Seq(m1, m2, m3).reduce(_ ++ _)

    try {
      val results = Await.result(runner, defaultTimeOut)
      println(results)
    } finally {
      db.close()
    }
    logger.info(s"Setting up db $config")
  }

  def tearDownDb(config: DatabaseConfig): Unit = {
    logger.info("Tearing ")
  }

  /**
    * This will delete the Jobs directory to create a clean jobs-root
    *
    * @param path Path to output jobs dir
    * @return
    */
  def setupJobDir(path: Path) = {
    if (Files.exists(path)) {
      logger.info(s"Deleting previous job dir $path")
      FileUtils.deleteDirectory(path.toFile)
    }
    logger.info(s"Creating job directory $path")
    Files.createDirectories(path)
    path
  }

  def cleanUpJobDir(path: Path) = {
    logger.info(s"Deleting job directory $path")
    FileUtils.deleteDirectory(path.toFile)
    path
  }

}

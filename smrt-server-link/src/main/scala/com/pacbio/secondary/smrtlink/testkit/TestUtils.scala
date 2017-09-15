package com.pacbio.secondary.smrtlink.testkit

import java.nio.file.{Files, Path}
import java.sql.SQLException

import com.pacbio.secondary.smrtlink.database.{DatabaseConfig, DatabaseUtils}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import resource._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._

/**
  * Testing Utils for Creating and dropping resources (e.g., db, job-root)
  *
  */
trait TestUtils extends DatabaseUtils with LazyLogging {

  /**
    * Drop the SMRT Link tables and flyway version_schema table
    * and recreate and run migrations.
    *
    * If the initial tables don't exists, they will created, but
    * a warning will be logged.
    *
    * @param config Database configuration
    */
  def setupDb(config: DatabaseConfig): Unit = {
    logger.info(s"Attempting setting up db $config with URI ${config.jdbcURI}")
    for (db <- managed(config.toDatabase); ds <- managed(config.toDataSource)) {
      val defaultTimeOut = 10.seconds

      def ignoreWithMessage(
          msg: String): PartialFunction[Throwable, Future[String]] = {
        case ex: SQLException => Future { s"$msg ${ex.getMessage}" }
      }

      val runner = for {
        m0 <- Future { TestConnection(ds) }
        m1 <- dropTables(db).recoverWith(
          ignoreWithMessage("Warning unable to drop smrtlink tables "))
        m2 <- dropFlywayTable(db).recoverWith(
          ignoreWithMessage("Warning unable to delete flyway table "))
        m3 <- Future { Migrator(ds) }.map(n =>
          s"Successfully ran $n migration(s)")
      } yield
        Seq(m0, m1, m2, m3).reduce { (acc, v) =>
          s"$acc.\n$v"
        }

      val results = Await.result(runner, defaultTimeOut)
      println(results)
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

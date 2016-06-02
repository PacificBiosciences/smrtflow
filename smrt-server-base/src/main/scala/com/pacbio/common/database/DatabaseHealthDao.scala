package com.pacbio.common.database

import com.pacbio.common.actors._
import com.pacbio.common.database.HealthDatabaseSchema._
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.logging.{LoggerFactoryProvider, Logger, LogResources}
import com.pacbio.common.models._
import com.pacbio.common.time.{ClockProvider, Clock}
import slick.driver.SQLiteDriver.api._
import slick.jdbc.meta._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object DatabaseHealthDao {
  val LOG_RESOURCE_ID: String = "health-db"
  val LOG_SOURCE_ID: String = "health-db"
}

/**
 * Concrete implementation of HealthDao that stores all messages in a database.
 */
class DatabaseHealthDao(
    databaseConfig: DatabaseConfig.Configured,
    clock: Clock,
    logger: Logger) extends AbstractHealthDao(clock) {

  private lazy val db = Database.forURL(databaseConfig.url, driver = databaseConfig.driver)

  db.run(MTable.getTables(healthGaugeMessageTable.baseTableRow.tableName).headOption).foreach[Any] { opt =>
    if (opt.isEmpty) {
      db.run(healthGaugeMessageTable.schema.create).onComplete {
        case Success(_) => logger.info(s"Created new Health Database with config $databaseConfig")
        case Failure(e) =>
          logger.error(s"Failed to create new Health Database with config $databaseConfig: $e")
          throw e
      }
    }
  }

  override def newHandler(id: String) = new HealthMessageHandler with BaseJsonProtocol {
    override def +=(message: HealthGaugeMessage): Unit = {
      try {
        db.run {
          healthGaugeMessageTable += HealthGaugeMessageRow(id, message)
        }
      } catch {
        case NonFatal(e) =>
          logger.error(s"Failed to write new health message to database: $e")
          throw e
      }
    }

    override def getAll: Future[Seq[HealthGaugeMessage]] = {
      try {
        db.run {
          healthGaugeMessageTable
            .filter(_.id === id)
            .sortBy(_.createdAt.asc)
            .result
        }.map(_.map(_.message))
      } catch {
        case NonFatal(e) =>
          logger.error(s"Failed to read health messages from database: $e")
          throw e
      }
    }
  }
}

/**
 * Provides a singleton DatabaseHealthDao. Concrete providers must mixin SetBindings, a ClockProvider, a
 * LogServiceActorRefProvider, and a BaseSmrtServerDatabaseConfigProviders that provides a configured health database.
 */
trait DatabaseHealthDaoProvider extends HealthDaoProvider {
  this: BaseSmrtServerDatabaseConfigProviders with ClockProvider with LoggerFactoryProvider =>

  import DatabaseHealthDao._

  override val healthDao: Singleton[HealthDao] = Singleton(() =>
    new DatabaseHealthDao(
      healthDaoDatabaseConfigProvider.databaseConfig().asInstanceOf[DatabaseConfig.Configured],
      clock(),
      loggerFactory().getLogger(LOG_RESOURCE_ID, LOG_SOURCE_ID)))

  val healthDatabaseLogResource: Singleton[LogResourceRecord] =
    Singleton(LogResourceRecord("Health Database Loggger", LOG_RESOURCE_ID, "Health Database"))
      .bindToSet(LogResources)
}

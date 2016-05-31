package com.pacbio.common.database

import com.pacbio.common.actors._
import com.pacbio.common.database.HealthDatabaseSchema._
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.logging.{LoggerFactoryProvider, Logger, LogResources}
import com.pacbio.common.models._
import com.pacbio.common.time.{ClockProvider, Clock}
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.jdbc.meta._

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

  db withSession { implicit session =>
    if(MTable.getTables(healthGaugeMessageTable.baseTableRow.tableName).list.isEmpty) {
      healthGaugeMessageTable.ddl.create

      logger.info(s"Created new Health Database with config $databaseConfig")
    }
  }

  override def newHandler(id: String) = new HealthMessageHandler with BaseJsonProtocol {
    override def +=(message: HealthGaugeMessage): Unit = {
      try {
        db withSession { implicit session =>
          healthGaugeMessageTable += HealthGaugeMessageRow(id, message)
        }
      } catch {
        case e: Exception =>
          logger.error(s"Failed to write new health message to database: $e")
          throw e
      }
    }

    override def getAll: Seq[HealthGaugeMessage] = {
      try {
        db withSession { implicit session =>
          healthGaugeMessageTable.filter(_.id === id).sortBy(_.createdAt.asc).run.map { row => row.message }
        }
      } catch {
        case e: Exception =>
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

package com.pacbio.common.database

import com.google.common.annotations.VisibleForTesting
import com.pacbio.common.actors._
import com.pacbio.common.database.LogDatabaseSchema._
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.time.{ClockProvider, Clock}
import com.typesafe.scalalogging.LazyLogging

import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.jdbc.meta.MTable

/**
 * Concrete implementation of LogDao that stores stale messages in a database.
 */
class DatabaseLogDao(
    databaseConfig: DatabaseConfig.Configured,
    clock: Clock,
    bufferSize: Int) extends AbstractLogDao(clock, bufferSize) with LazyLogging {

  private lazy val db = Database.forURL(databaseConfig.url, driver = databaseConfig.driver)

  db withSession { implicit session =>
    if (MTable.getTables(logMessageTable.baseTableRow.tableName).list.isEmpty) {
      logMessageTable.ddl.create

      logger.info(s"Created new Log Database with config $databaseConfig")
    }
  }

  override def newBuffer(id: String) = new LogBuffer with BaseJsonProtocol {

    override def handleStaleMessage(message: LogMessage): Unit = {
      db withSession { implicit session =>
        logMessageTable += LogMessageRow(id, message)
      }
    }

    override def searchStaleMessages(criteria: SearchCriteria, limit: Int): Seq[LogMessage] = {
      if (limit <= 0) return Nil

      db withSession { implicit session =>
        var query = logMessageTable.filter(_.id === id)

        if (criteria.substring.isDefined)
          query = query.filter(_.message.indexOf(criteria.substring.get) >= 0)
        if (criteria.sourceId.isDefined)
          query = query.filter(_.sourceId === criteria.sourceId.get)
        if (criteria.startTime.isDefined)
          query = query.filter(_.createdAt >= criteria.startTime.get)
        if (criteria.endTime.isDefined)
          query = query.filter(_.createdAt < criteria.endTime.get)

        // Note, we're querying the full list of matches and then reducing to the number of required results, rather
        // than applying a limit in the query. This is because of a bug in either Slick or the Sqlite driver that
        // creates unnecessary aliases, which are incompatible with the ORDER By ? OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        // syntax.
        // TODO(smcclellan): Take limit natively in query when Slick/Sqlite bug is resolved
        query
          .sortBy(_.createdAt.desc)
          // .drop(0)
          // .take(limit)
          .run
          .map { row => row.message }
          .take(limit)
          .reverse
      }
    }
  }

  @VisibleForTesting
  def deleteAll(): Unit = {
    db withSession { implicit session =>
      logMessageTable.delete.run
    }
  }
}

/**
 * Provides a singleton DatabaseLogDao. Concrete providers must mixin SetBindings and a ClockProvider and a
 * BaseSmrtServerDatabaseConfigProviders that provides a configured log database.
 */
trait DatabaseLogDaoProvider extends LogDaoProvider {
  this: BaseSmrtServerDatabaseConfigProviders with ClockProvider =>

  override val logDao: Singleton[LogDao] = Singleton(() =>
    new DatabaseLogDao(
      logDaoDatabaseConfigProvider.databaseConfig().asInstanceOf[DatabaseConfig.Configured],
      clock(),
      logDaoBufferSize))
}

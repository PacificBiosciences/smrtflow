package com.pacbio.common.database

import com.google.common.annotations.VisibleForTesting
import com.pacbio.common.actors._
import com.pacbio.common.database.LogDatabaseSchema._
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.time.{ClockProvider, Clock}
import com.typesafe.scalalogging.LazyLogging

import slick.driver.SQLiteDriver.api._
import slick.jdbc.meta.MTable

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

/**
 * Concrete implementation of LogDao that stores stale messages in a database.
 */
class DatabaseLogDao(
    databaseConfig: DatabaseConfig.Configured,
    clock: Clock,
    bufferSize: Int) extends AbstractLogDao(clock, bufferSize) with LazyLogging {

  private lazy val db = Database.forURL(databaseConfig.url, driver = databaseConfig.driver)

  db.run(MTable.getTables(logMessageTable.baseTableRow.tableName).headOption).foreach[Any] { opt =>
    if (opt.isEmpty) db.run(logMessageTable.schema.create)
  }

  override def newBuffer(id: String) = new LogBuffer with BaseJsonProtocol {

    override def handleStaleMessage(message: LogMessage): Future[Unit] =
      db.run(logMessageTable += LogMessageRow(id, message)).map(_ => ())

    override def searchStaleMessages(criteria: SearchCriteria, limit: Int): Future[Seq[LogMessage]] = {
      if (limit <= 0) return Future(Nil)

      var query = logMessageTable.filter(_.id === id)

      if (criteria.substring.isDefined)
        query = query.filter(_.message.indexOf(criteria.substring.get) >= 0)
      if (criteria.sourceId.isDefined)
        query = query.filter(_.sourceId === criteria.sourceId.get)
      if (criteria.startTime.isDefined)
        query = query.filter(_.createdAt >= criteria.startTime.get)
      if (criteria.endTime.isDefined)
        query = query.filter(_.createdAt < criteria.endTime.get)

      query = query.sortBy(_.createdAt.desc).take(limit)

      db.run(query.result)
        .map(_.reverse)
        .map(_.map(_.message))
    }
  }

  @VisibleForTesting
  override def clear(): Future[Unit] =
    Future.sequence(Seq(db.run(logMessageTable.delete), super.clear())).map(_ => ())
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

package com.pacbio.common.database

import java.util.UUID

import com.google.common.annotations.VisibleForTesting
import com.pacbio.common.actors._
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors.{ResourceNotFoundError, UnprocessableEntityError}
import com.pacbio.common.time.{ClockProvider, Clock}
import com.typesafe.scalalogging.LazyLogging

import slick.driver.SQLiteDriver.api._
import slick.jdbc.meta.MTable

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

/**
 * Concrete implementation of LogDao that stores messages in a database.
 */
class DatabaseLogDao(db: Database, clock: Clock, responseSize: Int) extends LogDao with LazyLogging {
  import LogDaoConstants._
  import TableModels._

  val NO_CRITERIA = SearchCriteria(None, None, None, None)

  def init(): Future[Unit] = {
    val createTables = MTable.getTables(logResources.baseTableRow.tableName).headOption.flatMap { r =>
      MTable.getTables(logMessages.baseTableRow.tableName).headOption.map { m =>
        Seq(r.map(_ => logResources), m.map(_ => logMessages))
      }
    }.flatMap { t =>
      DBIO.sequence(t.filter(_.isDefined).map(_.get).map(_.schema.create))
    }
    val actions = createTables >> {
      logResources.filter(_.id === LogDaoConstants.SYSTEM_ID).result.headOption.flatMap { s =>
        if (s.isEmpty) {
          val systemResource =
            LogResource(clock.dateNow(), SYSTEM_RESOURCE.description, SYSTEM_ID, SYSTEM_RESOURCE.name)
          (logResources += systemResource).map(_ => ())
        } else DBIO.successful()
      }
    }
    db.run(actions.transactionally)
  }

  override def getAllLogResources: Future[Seq[LogResource]] = db.run(logResources.result)

  override def createLogResource(m: LogResourceRecord): Future[String] =
    if (m.id == SYSTEM_ID)
      Future.failed(new UnprocessableEntityError(s"Resource with id $SYSTEM_ID is reserved for the system log"))
    else {
      val create = logResources.filter(_.id === m.id).result.headOption.flatMap { r =>
        if (r.isEmpty) {
          val resource = LogResource(clock.dateNow(), m.description, m.id, m.name)
          (logResources += resource).map(_ => resource)
        } else
          DBIO.failed(new UnprocessableEntityError(s"Resource with id ${m.id} already exists"))
      }
      db.run(create.transactionally).map(_ => s"Successfully created resource ${m.id}")
    }

  override def getLogResource(id: String): Future[LogResource] =
    db.run {
      logResources.filter(_.id === id).result.headOption.map {
        case Some(r) => r
        case None => throw new ResourceNotFoundError(s"Unable to find resource $id")
      }
    }

  override def getLogMessages(id: String): Future[Seq[LogMessage]] = searchLogMessages(id, NO_CRITERIA)

  override def createLogMessage(id: String, m: LogMessageRecord): Future[LogMessage] =
    db.run {
      val message = LogMessage(clock.dateNow(), UUID.randomUUID(), m.message, m.level, m.sourceId)
      val create = (logMessages += LogMessageRow(id, message)) >> (logMessages += LogMessageRow(SYSTEM_ID, message))
      create.map(_ => message).transactionally
    }

  override def getSystemLogMessages: Future[Seq[LogMessage]] = getLogMessages(SYSTEM_ID)

  override def searchLogMessages(id: String, criteria: SearchCriteria): Future[Seq[LogMessage]] = db.run {
    var q = logMessages.filter(_.resourceId === id)
    criteria.substring.foreach { s => q = q.filter(_.message.substring(s)) }
    criteria.sourceId.foreach { s => q = q.filter(_.sourceId === s) }
    criteria.startTime.foreach { t => q = q.filter(_.createdAt >= t) }
    criteria.endTime.foreach { t => q = q.filter(_.createdAt < t) }
    q.sortBy(_.createdAt.desc).take(responseSize).result
  }.map(_.map(_.message))

  override def searchSystemLogMessages(criteria: SearchCriteria): Future[Seq[LogMessage]] =
    searchLogMessages(SYSTEM_ID, criteria)

  @VisibleForTesting
  def deleteAll(): Unit = {
    db.run(logMessages.delete)
  }
}

/**
 * Provides a singleton DatabaseLogDao. Concrete providers must mixin SetBindings and a ClockProvider and a
 * DatabaseProvider.
 */
trait DatabaseLogDaoProvider extends LogDaoProvider {
  this: DatabaseProvider with ClockProvider =>

  override val logDao: Singleton[LogDao] = Singleton(() => new DatabaseLogDao(db(), clock(), logDaoResponseSize))
}

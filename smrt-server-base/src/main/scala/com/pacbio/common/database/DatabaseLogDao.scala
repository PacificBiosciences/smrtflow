package com.pacbio.common.database

import java.util.UUID

import com.google.common.annotations.VisibleForTesting
import com.pacbio.common.actors._
import com.pacbio.common.dependency.{RequiresInitialization, InitializationComposer, Singleton}
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors.{ResourceNotFoundError, UnprocessableEntityError}
import com.pacbio.common.time.{ClockProvider, Clock}
import slick.driver.SQLiteDriver

import slick.driver.SQLiteDriver.api._
import slick.jdbc.meta.MTable

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}

/**
 * Concrete implementation of LogDao that stores messages in a database.
 */
class DatabaseLogDao(db: Database, clock: Clock, responseSize: Int) extends LogDao with RequiresInitialization {
  import LogDaoConstants._
  import TableModels._

  val NO_CRITERIA = SearchCriteria(None, None, None, None)
  val SYSTEM = LogResource(clock.dateNow(), SYSTEM_RESOURCE.description, SYSTEM_ID, SYSTEM_RESOURCE.name)

  override def init(): String = {
    val resTable = MTable.getTables(logResources.baseTableRow.tableName).headOption
    val msgTable = MTable.getTables(logMessages.baseTableRow.tableName).headOption

    val createMissingTables = DBIO.sequence(Seq(resTable, msgTable)).flatMap { tables =>
      val r = tables.head.isEmpty
      val m = tables(1).isEmpty

      val schemas: ArrayBuffer[SQLiteDriver.SchemaDescription] = new ArrayBuffer()
      if (r) schemas += logResources.schema
      if (m) schemas += logMessages.schema

      if (schemas.isEmpty) DBIO.successful(()) else schemas.reduce(_ ++ _).create
    }

    Await.result(db.run(createMissingTables).map(_ => "Log Tables Created"), 10.seconds)
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
    if (id == SYSTEM_ID) Future.successful(SYSTEM) else db.run {
      logResources.filter(_.id === id).result.headOption.map {
        case Some(r) => r
        case None => throw new ResourceNotFoundError(s"Unable to find resource $id")
      }
    }

  override def getLogMessages(id: String): Future[Seq[LogMessage]] = search(Some(id), NO_CRITERIA)

  override def createLogMessage(id: String, m: LogMessageRecord): Future[LogMessage] =
    db.run {
      val message = LogMessage(clock.dateNow(), UUID.randomUUID(), m.message, m.level, m.sourceId)
      val create = logMessages += LogMessageRow(id, message)
      create.map(_ => message)
    }

  override def getSystemLogMessages: Future[Seq[LogMessage]] = search(None, NO_CRITERIA)

  override def searchLogMessages(id: String, criteria: SearchCriteria): Future[Seq[LogMessage]] =
    search(Some(id), criteria)

  override def searchSystemLogMessages(criteria: SearchCriteria): Future[Seq[LogMessage]] = search(None, criteria)

  private def search(id: Option[String], criteria: SearchCriteria): Future[Seq[LogMessage]] = db.run {
    var q: Query[LogMessageT, LogMessageRow, Seq] = logMessages
    id.foreach { i => q = q.filter(_.resourceId === i) }
    criteria.substring.foreach { s => q = q.filter(_.message.indexOf(s) >= 0) }
    criteria.sourceId.foreach { s => q = q.filter(_.sourceId === s) }
    criteria.startTime.foreach { t => q = q.filter(_.createdAt >= t) }
    criteria.endTime.foreach { t => q = q.filter(_.createdAt < t) }
    q.sortBy(_.createdAt.asc).take(responseSize).result
  }.map(_.map(_.message))

  @VisibleForTesting
  def deleteAll(): Future[Int] = db.run(logMessages.delete >> logResources.delete)
}

/**
 * Provides a singleton DatabaseLogDao. Concrete providers must mixin SetBindings and a ClockProvider and a
 * DatabaseProvider, and provide a value for logDbUri.
 */
trait DatabaseLogDaoProvider extends LogDaoProvider {
  this: DatabaseProvider with ClockProvider with InitializationComposer =>

  val logDbURI: Singleton[String]

  override val logDao: Singleton[LogDao] =
    requireInitialization(Singleton(() => new DatabaseLogDao(db(logDbURI()), clock(), logDaoResponseSize)))
}

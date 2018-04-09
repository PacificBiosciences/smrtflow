package com.pacbio.secondary.smrtlink.actors

import java.util.UUID

import akka.actor.ActorRef
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.{
  ResourceNotFoundError,
  UnprocessableEntityError
}
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  EngineJob,
  JobEvent
}
import com.pacbio.secondary.smrtlink.database.TableModels
import com.pacbio.secondary.smrtlink.models._
import com.pacificbiosciences.pacbiobasedatamodel.SupportedRunStates
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import slick.jdbc.PostgresProfile.api._
import org.joda.time.{DateTime => JodaDateTime}

import scala.collection.mutable
import scala.util.control.NonFatal
import scala.util.{Success, Try}

/**
  * RunDao that stores run designs in a Slick database.
  */
class RunDao(val db: Database, val parser: DataModelParser)
    extends DaoFutureUtils
    with LazyLogging
    with EventComponent {

  import TableModels._

  private val eventListeners: mutable.MutableList[ActorRef] =
    new mutable.MutableList()

  // This is added to get around potential circular dependencies of the listener
  def addListener(listener: ActorRef): Unit = {
    logger.info(s"Adding Listener $listener to $this")
    eventListeners += listener
  }

  override def sendEventToManager[T](message: T): Unit = {
    eventListeners.foreach(a => a ! message)
  }

  private def updateOrCreate(
      uniqueId: UUID,
      update: Boolean,
      parseResults: Option[ParseResults] = None,
      setReserved: Option[Boolean] = None): Future[RunSummary] = {

    require(update || parseResults.isDefined,
            "Cannot create a run without ParseResults")

    val action =
      runSummaries.filter(_.uniqueId === uniqueId).result.headOption.flatMap {
        prev =>
          if (prev.isEmpty && update)
            throw ResourceNotFoundError(s"Unable to find resource $uniqueId")
          if (prev.isDefined && !update)
            throw UnprocessableEntityError(
              s"Resource $uniqueId already exists")

          val wasReserved = prev.map(_.reserved)
          val reserved = setReserved.orElse(wasReserved).getOrElse(false)
          val summary = parseResults
            .map(_.run.summarize)
            .orElse(prev)
            .get
            .copy(reserved = reserved)

          val summaryUpdate =
            Seq(runSummaries.insertOrUpdate(summary).map(_ => summary))

          val dataModelAndCollectionsUpdate = parseResults
            .map { res: ParseResults =>
              if (res.run.uniqueId != uniqueId)
                throw UnprocessableEntityError(
                  s"Cannot update run $uniqueId with data model for run ${res.run.uniqueId}")

              val collectionsUpdate =
                res.collections.map(c => collectionMetadata.insertOrUpdate(c))
              val dataModelUpdate = dataModels.insertOrUpdate(
                DataModelAndUniqueId(res.run.dataModel, uniqueId))
              collectionsUpdate :+ dataModelUpdate
            }
            .getOrElse(Nil)

          DBIO
            .sequence(summaryUpdate ++ dataModelAndCollectionsUpdate)
            .map(_ => summary)
      }

    val fx = db.run(action.transactionally)

    fx.foreach { runSummary =>
      sendEventToManager[RunChangedStateMessage](
        RunChangedStateMessage(runSummary))
    }

    fx
  }

  def getRuns(criteria: SearchCriteria): Future[Set[RunSummary]] = {

    val q0 = runSummaries

    val q1 =
      criteria.chipType.map(c => q0.filter(_.chipType === c)).getOrElse(q0)

    val q2 = criteria.name.map(n => q1.filter(_.name === n)).getOrElse(q1)

    val q3 = criteria.substring
      .map { sx =>
        q2.filter { r =>
          (r.name.indexOf(sx) >= 0)
            .||(r.summary.getOrElse("").indexOf(sx) >= 0)
        }
      }
      .getOrElse(q2)

    val q4 =
      criteria.reserved.map(c => q3.filter(_.reserved === c)).getOrElse(q3)

    val q5 = criteria.createdBy
      .map(c => q4.filter(_.createdBy.isDefined).filter(_.createdBy === c))
      .getOrElse(q4)

    db.run(q5.result).map(_.toSet)
  }

  def getRun(id: UUID): Future[Run] = {
    val summary = runSummaries.filter(_.uniqueId === id)
    val model = dataModels.filter(_.uniqueId === id)
    val run = summary.join(model).result.headOption.map { opt =>
      opt
        .map { res =>
          res._1.withDataModel(res._2.dataModel)
        }
        .getOrElse {
          throw ResourceNotFoundError(s"Unable to find resource $id")
        }
    }

    db.run(run)
  }

  def createRun(create: RunCreate): Future[RunSummary] =
    Future(parser(create.dataModel)).flatMap { r =>
      updateOrCreate(r.run.uniqueId, update = false, Some(r), None)
    }

  def updateRun(id: UUID, update: RunUpdate): Future[RunSummary] =
    Future(update.dataModel.map(parser.apply)).flatMap { r =>
      updateOrCreate(id, update = true, r, update.reserved)
    }

  def deleteRun(id: UUID): Future[MessageResponse] = {
    val action = DBIO
      .seq(
        collectionMetadata.filter(_.runId === id).delete,
        dataModels.filter(_.uniqueId === id).delete,
        runSummaries.filter(_.uniqueId === id).delete
      )
      .map(_ => MessageResponse(s"Successfully deleted run design $id"))
    db.run(action.transactionally)
  }

  def getCollectionMetadatas(runId: UUID): Future[Seq[CollectionMetadata]] =
    db.run(collectionMetadata.filter(_.runId === runId).result)

  def getCollectionMetadata(runId: UUID,
                            uniqueId: UUID): Future[CollectionMetadata] = {
    db.run {
        collectionMetadata
          .filter(_.runId === runId)
          .filter(_.uniqueId === uniqueId)
          .result
          .headOption
      }
      .map(_.getOrElse(throw ResourceNotFoundError(
        s"No collection with id $uniqueId found in run $runId")))

  }
}

/**
  * Provides a RunDao.
  */
trait RunDaoProvider { this: DalProvider with DataModelParserProvider =>

  val runDao: Singleton[RunDao] =
    Singleton(() => new RunDao(db(), dataModelParser()))
}

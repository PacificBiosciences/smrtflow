package com.pacbio.secondary.smrtlink.database

import java.util.UUID

import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.{UnprocessableEntityError, ResourceNotFoundError}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import slick.driver.SQLiteDriver.api._


/**
 * RunDao that stores run designs in a Slick database.
 */
class DatabaseRunDao(db: Database, parser: DataModelParser) extends RunDao {
  import TableModels._

  private def updateOrCreate(
      uniqueId: UUID,
      update: Boolean,
      parseResults: Option[ParseResults] = None,
      setReserved: Option[Boolean] = None): Future[RunSummary] = {

    require(update || parseResults.isDefined, "Cannot create a run without ParseResults")

    val action = runSummaries.filter(_.uniqueId === uniqueId).result.headOption.flatMap { prev =>
      if (prev.isEmpty && update)
        throw new ResourceNotFoundError(s"Unable to find resource $uniqueId")
      if (prev.isDefined && !update)
        throw new UnprocessableEntityError(s"Resource $uniqueId already exists")

      val wasReserved = prev.map(_.reserved)
      val reserved = setReserved.orElse(wasReserved).getOrElse(false)
      val summary = parseResults.map(_.run.summarize).orElse(prev).get.copy(reserved = reserved)

      val summaryUpdate = Seq(runSummaries.insertOrUpdate(summary).map(_ => summary))

      val dataModelAndCollectionsUpdate = parseResults.map { res =>
        if (res.run.uniqueId != uniqueId)
          throw new UnprocessableEntityError(s"Cannot update run $uniqueId with data model for run ${res.run.uniqueId}")

        Seq(
          dataModels.insertOrUpdate(DataModelAndUniqueId(res.run.dataModel, uniqueId)),
          collectionMetadata ++= res.collections
        )
      }.getOrElse(Nil)

      DBIO.sequence(summaryUpdate ++ dataModelAndCollectionsUpdate).map(_ => summary)
    }

    db.run(action.transactionally)
  }

  override def getRuns(criteria: SearchCriteria): Future[Set[RunSummary]] = {
    var query: Query[RunSummariesT, RunSummariesT#TableElementType, Seq] = runSummaries

    if (criteria.name.isDefined)
      query = query.filter(_.name === criteria.name.get)

    if (criteria.substring.isDefined)
      query = query.filter { r =>
        (r.name.indexOf(criteria.substring.get) >= 0).||(r.summary.getOrElse("").indexOf(criteria.substring.get) >= 0)
      }

    if (criteria.createdBy.isDefined)
      query = query.filter(_.createdBy.isDefined).filter(_.createdBy === criteria.createdBy)

    if (criteria.reserved.isDefined)
      query = query.filter(_.reserved === criteria.reserved.get)

    db.run(query.result).map(_.toSet)
  }

  override def getRun(id: UUID): Future[Run] = {
    val summary = runSummaries.filter(_.uniqueId === id)
    val model = dataModels.filter(_.uniqueId === id)
    val run = summary.join(model).result.headOption.map { opt =>
      opt.map { res =>
        res._1.withDataModel(res._2.dataModel)
      }.getOrElse {
        throw new ResourceNotFoundError(s"Unable to find resource $id")
      }
    }
    
    db.run(run)
  }

  override def createRun(create: RunCreate): Future[RunSummary] = {
    val parseResults = parser(create.dataModel)
    updateOrCreate(parseResults.run.uniqueId, update = false, Some(parseResults))
  }

  override def updateRun(id: UUID, update: RunUpdate): Future[RunSummary] = {
    val parseResults = update.dataModel.map(parser.apply)
    updateOrCreate(id, update = true, parseResults, update.reserved)
  }

  override def deleteRun(id: UUID): Future[String] = {
    val action = DBIO.seq(
      collectionMetadata.filter(_.runId === id).delete,
      dataModels.filter(_.uniqueId === id).delete,
      runSummaries.filter(_.uniqueId === id).delete
    ).map(_ => s"Successfully deleted run design $id")
    db.run(action.transactionally)
  }

  override def getCollectionMetadatas(runId: UUID): Future[Seq[CollectionMetadata]] =
    db.run(collectionMetadata.filter(_.runId === runId).result)

  override def getCollectionMetadata(runId: UUID, uniqueId: UUID): Future[CollectionMetadata] = {
    db.run {
      collectionMetadata
        .filter(_.runId === runId)
        .filter(_.uniqueId === uniqueId)
        .result
        .headOption
    }.map(_.getOrElse(throw new ResourceNotFoundError(s"No collection with id $uniqueId found in run $runId")))

  }
}

/**
 * Provides a DatabaseRunDao.
 */
trait DatabaseRunDaoProvider extends RunDaoProvider {
  this: SmrtLinkConfigProvider with DataModelParserProvider =>

  override val runDao: Singleton[RunDao] =
    Singleton(() => new DatabaseRunDao(db(), dataModelParser()))
}
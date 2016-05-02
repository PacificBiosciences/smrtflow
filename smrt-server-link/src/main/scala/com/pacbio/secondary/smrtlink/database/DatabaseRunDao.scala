package com.pacbio.secondary.smrtlink.database

import java.util.UUID

import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.{UnprocessableEntityError, ResourceNotFoundError}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.models._

import scala.slick.driver.SQLiteDriver.simple._
import scala.util.control.NonFatal

/**
 * RunDao that stores run designs in a Slick database.
 */
class DatabaseRunDao(dal: Dal, parser: DataModelParser) extends RunDao {
  import TableModels._

  private def putResults(results: ParseResults, update: Boolean = false)(implicit session: Session): Unit = {
    try {
      var reserved = Seq.empty[Boolean]
      if (update) {
        if (runSummaries.filter(_.uniqueId === results.run.uniqueId).size.run != 1)
          throw new ResourceNotFoundError(s"Unable to find resource ${results.run.uniqueId}")
        collectionMetadata.filter(_.runId === results.run.uniqueId).delete
        dataModels.filter(_.uniqueId === results.run.uniqueId).delete
        reserved = runSummaries.filter(_.uniqueId === results.run.uniqueId).map(_.reserved).run
        runSummaries.filter(_.uniqueId === results.run.uniqueId).delete
      } else if (runSummaries.filter(_.uniqueId === results.run.uniqueId).size.run != 0)
          throw new UnprocessableEntityError(s"Resource ${results.run.uniqueId} already exists")

      runSummaries += results.run.summarize.copy(reserved = reserved.headOption.getOrElse(false))
      dataModels += DataModelAndUniqueId(results.run.dataModel, results.run.uniqueId)
      results.collections.foreach { c => collectionMetadata += c }
    } catch {
      case NonFatal(e) =>
        session.rollback()
        throw e
    }
  }

  override def getRuns(criteria: SearchCriteria): Set[RunSummary] = {
    dal.db withSession { implicit session =>
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

      query.run.toSet
    }
  }

  override def getRun(id: UUID): Run = {
    try {
      val result = dal.db withSession { implicit session =>
        val summary = runSummaries.filter(_.uniqueId === id)
        val model = dataModels.filter(_.uniqueId === id)
        summary.join(model).first
      }

      result._1.withDataModel(result._2.dataModel)
    } catch {
      case e: NoSuchElementException => throw new ResourceNotFoundError(s"Unable to find resource $id")
    }
  }

  override def createRun(create: RunCreate): RunSummary = {
    val results = parser(create.dataModel)

    dal.db.withTransaction { implicit session =>
      putResults(results, update = false)
    }

    results.run.summarize
  }

  override def updateRun(id: UUID, update: RunUpdate): RunSummary = {
    dal.db.withTransaction { implicit session =>
      update.dataModel.foreach { d =>
        val results = parser(d)
        if (results.run.uniqueId != id)
          throw new UnprocessableEntityError(s"Cannot update run $id with data model for run ${results.run.uniqueId}")
        putResults(results, update = true)
      }

      update.reserved.foreach { r =>
        val query = for { s <- runSummaries if s.uniqueId === id } yield s
        val run = query.first.copy(reserved = r)
        query.update(run)
      }

      runSummaries.filter(_.uniqueId === id).first
    }
  }

  override def deleteRun(id: UUID): String = {
    dal.db.withTransaction { implicit session =>
      collectionMetadata.filter(_.runId === id).delete
      dataModels.filter(_.uniqueId === id).delete
      runSummaries.filter(_.uniqueId === id).delete

      s"Successfully deleted run design $id"
    }
  }

  override def getCollectionMetadatas(runId: UUID): Seq[CollectionMetadata] = {
    dal.db withSession { implicit session =>
      collectionMetadata
          .filter(_.runId === runId)
          .run
    }
  }

  override def getCollectionMetadata(runId: UUID, uniqueId: UUID): CollectionMetadata = {
    dal.db withSession { implicit session =>
      collectionMetadata
          .filter(_.runId === runId)
          .filter(_.uniqueId === uniqueId)
          .run
          .head
    }
  }
}

/**
 * Provides a DatabaseRunDao.
 */
trait DatabaseRunDaoProvider extends RunDaoProvider {
  this: DalProvider with DataModelParserProvider =>

  override val runDao: Singleton[RunDao] =
    Singleton(() => new DatabaseRunDao(dal(), dataModelParser()))
}
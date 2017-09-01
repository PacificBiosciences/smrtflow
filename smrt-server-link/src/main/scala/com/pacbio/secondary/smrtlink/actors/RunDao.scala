package com.pacbio.secondary.smrtlink.actors

import java.util.UUID

import com.google.common.annotations.VisibleForTesting
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.{UnprocessableEntityError, ResourceNotFoundError}
import CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.models._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.language.implicitConversions

/**
 * Interface for the Run service DAO.
 */
trait RunDao {
  /**
   * Provides a list of all available run designs.
   */
  def getRuns(criteria: SearchCriteria): Future[Set[RunSummary]]

  /**
   * Gets a run design by id.
   */
  def getRun(id: UUID): Future[Run]

  /**
   * Creates a new run design.
   */
  def createRun(create: RunCreate): Future[RunSummary]

  /**
   * Updates a run design.
   */
  def updateRun(id: UUID, update: RunUpdate): Future[RunSummary]

  /**
   * Deletes a run design.
   */
  def deleteRun(id: UUID): Future[MessageResponse]

  /**
   * Provides a list of all CollectionMetadata for a given run.
   */
  def getCollectionMetadatas(runId: UUID): Future[Seq[CollectionMetadata]]

  /**
   * Gets a CollectionMetadata by unique id.
   */
  def getCollectionMetadata(runId: UUID, id: UUID): Future[CollectionMetadata]
}

/**
 * Provider for injecting a singleton RunDao. Concrete providers must override the runDao val.
 */
trait RunDaoProvider {
  /**
   * Singleton run design DAO object.
   */
  val runDao: Singleton[RunDao]
}

/**
 * Concrete implementation of RunDao that stores runs in memory.
 */
class InMemoryRunDao(parser: DataModelParser) extends RunDao {
  val runs: mutable.HashMap[UUID, Run] = new mutable.HashMap
  val collections: mutable.HashMap[UUID, mutable.HashMap[UUID, CollectionMetadata]] = new mutable.HashMap

  private def putResults(results: ParseResults): Unit = {
    // Ignore value of reserved in results, it's automatically false
    var reserved = false
    if (runs contains results.run.uniqueId) reserved = runs(results.run.uniqueId).reserved
    runs(results.run.uniqueId) = results.run.copy(reserved = reserved)

    val collectMap = new mutable.HashMap[UUID, CollectionMetadata]
    results.collections.foreach { c => collectMap(c.uniqueId) = c }
    collections(results.run.uniqueId) = collectMap
  }

  override final def getRuns(criteria: SearchCriteria): Future[Set[RunSummary]] = Future {
    var results: Iterable[Run] = runs.values
    if (criteria.name.isDefined)
      results = results.filter(_.name == criteria.name.get)
    if (criteria.substring.isDefined)
      results = results.filter(run =>
        run.name.contains(criteria.substring.get) || run.summary.exists(_.contains(criteria.substring.get)))
    if (criteria.createdBy.isDefined)
      results = results.filter(_.createdBy.exists(_ == criteria.createdBy.get))
    if (criteria.reserved.isDefined)
      results = results.filter(_.reserved == criteria.reserved.get)
    results.map(_.summarize).toSet
  }

  override final def getRun(id: UUID): Future[Run] = Future {
    if (runs contains id)
      runs(id)
    else
      throw new ResourceNotFoundError(s"Unable to find resource $id")
  }

  override final def createRun(create: RunCreate): Future[RunSummary] = Future {
    val results = parser(create.dataModel)
    putResults(results)
    results.run.summarize
  }

  override final def updateRun(id: UUID, update: RunUpdate): Future[RunSummary] = Future {
    if (runs contains id) {
      update.dataModel.foreach { d =>
        val results = parser(d)
        if (results.run.uniqueId != id)
          throw new UnprocessableEntityError(s"Cannot update run $id with data model for run ${results.run.uniqueId}")
        putResults(results)
      }
      update.reserved.foreach(r => runs(id) = runs(id).copy(reserved = r))
      runs(id).summarize
    } else
      throw new ResourceNotFoundError(s"Unable to find resource $id")
  }

  override final def deleteRun(id: UUID): Future[MessageResponse] = Future {
    if (runs contains id) {
      runs -= id
      collections -= id
      MessageResponse(s"Successfully deleted run design $id")
    } else
      throw new ResourceNotFoundError(s"Unable to find resource $id")
  }

  override final def getCollectionMetadatas(runId: UUID): Future[Seq[CollectionMetadata]] = Future {
    if (collections contains runId)
      collections(runId).values.toSeq
    else
      throw new ResourceNotFoundError(s"Unable to find resource $runId")
  }

  override final def getCollectionMetadata(runId: UUID, id: UUID): Future[CollectionMetadata] = Future {
    if (collections contains runId)
      if (collections(runId) contains id)
        collections(runId)(id)
      else
        throw new ResourceNotFoundError(s"Unable to find resource $id")
    else
      throw new ResourceNotFoundError(s"Unable to find resource $runId")
  }

  @VisibleForTesting
  def clear(): Unit = {
    runs.clear()
    collections.clear()
  }
}

/**
 * Provides an InMemoryRunDao. Concrete providers must mixin a ClockProvider.
 */
trait InMemoryRunDaoProvider extends RunDaoProvider {
  this: DataModelParserProvider =>

  override final val runDao: Singleton[RunDao] =
    Singleton(() => new InMemoryRunDao(dataModelParser()))
}

package com.pacbio.secondary.smrtlink.actors

import java.util.UUID

import com.google.common.annotations.VisibleForTesting
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.{
  UnprocessableEntityError,
  ResourceNotFoundError
}
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

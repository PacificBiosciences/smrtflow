package com.pacbio.secondary.smrtlink.database

import java.util.UUID

import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.{
  ResourceNotFoundError,
  UnprocessableEntityError
}
import com.pacbio.secondary.smrtlink.time.{Clock, ClockProvider}
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.{
  DalProvider,
  SampleDao,
  SampleDaoProvider
}
import com.pacbio.secondary.smrtlink.database.TableModels._
import com.pacbio.secondary.smrtlink.models._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import slick.driver.PostgresDriver.api._

class DatabaseSampleDao(db: Database, clock: Clock) extends SampleDao {

  /*
   * Returns a Set() of all samples in the database
   */
  def getSamples(): Future[Set[Sample]] = db.run(samples.result).map(_.toSet)

  /*
   * Returns a single sample matching the passed uniqueId
   * Throws if the uniqueId was not found (or was more than once)
   */
  def getSample(uniqueId: UUID): Future[Sample] =
    db.run(samples.filter(_.uniqueId === uniqueId).result.headOption)
      .map(_.getOrElse(
        throw new ResourceNotFoundError(s"Unable to find sample $uniqueId")))

  /*
   * Checks if a UUID is already a known sample in the DB, and if so returns true
   */
  def exists(uniqueId: UUID): Future[Boolean] =
    db.run(samples.filter(_.uniqueId === uniqueId).exists.result)

  /*
   * Creates a new sample in the database
   */
  def createSample(login: String, create: SampleCreate): Future[Sample] = {
    val insert = samples
      .filter(_.uniqueId === create.uniqueId)
      .exists
      .result
      .map { ex =>
        if (ex)
          throw new UnprocessableEntityError(
            s"Sample with uuid ${create.uniqueId} already exists.")
      }
      .flatMap { _ =>
        val sample = Sample(
          create.details,
          create.uniqueId,
          create.name,
          login,
          clock.dateNow()
        )
        (samples += sample).map(_ => sample)
      }
    db.run(insert.transactionally)
  }

  /*
   * Updates an existing sample in the database
   * Throws if the uniqueId was not found
   */
  def updateSample(uniqueId: UUID, update: SampleUpdate): Future[Sample] = {
    val actions: Seq[DBIOAction[Int, NoStream, Effect.Write]] = Seq(
      update.details.map(d =>
        samples.filter(_.uniqueId === uniqueId).map(_.details).update(d)),
      update.name.map(n =>
        samples.filter(_.uniqueId === uniqueId).map(_.name).update(n))
    ).filter(_.isDefined).map(_.get)

    val totalAction = DBIO.sequence(actions).flatMap { counts =>
      if (counts.contains(0)) {
        throw new ResourceNotFoundError(s"Unable to find sample $uniqueId")
      }
      samples.filter(_.uniqueId === uniqueId).result.head
    }

    db.run(totalAction)
  }

  /*
   * Deletes an existing sample in the database
   * Throws if the resource was not found or if more than one was found
   */
  def deleteSample(uniqueId: UUID): Future[MessageResponse] = {
    val action = samples
      .filter(_.uniqueId === uniqueId)
      .delete
      .map { count =>
        if (count == 1) {
          MessageResponse(s"Successfully deleted sample $uniqueId")
        } else if (count == 0) {
          throw new ResourceNotFoundError(s"Unable to find sample $uniqueId")
        } else {
          throw new IllegalStateException(
            s"More than one sample found with id $uniqueId")
        }
      }
      .transactionally

    db.run(action)
  }
}

/**
  * Provides a DatabaseRunDao.
  */
trait DatabaseSampleDaoProvider extends SampleDaoProvider with ClockProvider {
  this: DalProvider with DataModelParserProvider =>

  //
  //this: DalProvider with DataModelParserProvider =>

  override final val sampleDao: Singleton[SampleDao] =
    Singleton(() => new DatabaseSampleDao(db(), clock()))
}

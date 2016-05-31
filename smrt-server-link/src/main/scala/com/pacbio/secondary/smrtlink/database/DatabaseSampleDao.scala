package com.pacbio.secondary.smrtlink.database

import java.util.UUID

import com.pacbio.common.services.PacBioServiceErrors.{ResourceNotFoundError, UnprocessableEntityError}
import com.pacbio.common.time.Clock
import com.pacbio.secondary.smrtlink.actors.{Dal, SampleDao}
import com.pacbio.secondary.smrtlink.database.TableModels._
import com.pacbio.secondary.smrtlink.models._

import scala.slick.driver.SQLiteDriver.simple._

class DatabaseSampleDao(dal: Dal, clock: Clock) extends SampleDao {

  /*
   * Returns a Set() of all samples in the database
   */
  def getSamples(): Set[Sample] = {
    dal.db withSession { implicit session =>
      samples.run.toSet
    }
  }

  /*
   * Returns a single sample matching the passed uniqueId
   * Throws if the uniqueId was not found (or was more than once)
   */
  def getSample(uniqueId: UUID): Sample = {
    dal.db withSession { implicit session =>
      val sample = samples.filter(_.uniqueId === uniqueId)
      if (1 != sample.size) {
        throw new ResourceNotFoundError(s"Unable to find sample $uniqueId")
      }
      sample.first
    }
  }

  /*
   * Checks if a UUID is already a known sample in the DB, and if so returns true
   */
  def exists(uniqueId: UUID): Boolean = {
    try {
      dal.db withSession { implicit session =>
        val sample = samples.filter(_.uniqueId === uniqueId)
        true
      }
    } catch {
      case e: ResourceNotFoundError =>
        false
    }
  }

  /*
   * Creates a new sample in the database
   */
  def createSample(login: String, create: SampleCreate): Sample = {
    dal.db.withTransaction { implicit session =>
      if (exists(create.uniqueId))
        throw new UnprocessableEntityError(s"Unable to create sample with uuid ${create.uniqueId}, already in use.")

      val sample = Sample(
        create.details,
        create.uniqueId,
        create.name,
        login,
        clock.dateNow()
      )

      samples += sample
      sample
    }
  }

  /*
   * Updates an existing sample in the database
   * Throws if the uniqueId was not found
   */
  def updateSample(uniqueId: UUID, update: SampleUpdate): Sample = {
    dal.db.withTransaction { implicit session =>
      var detailsCount: Option[Int] = None
      var nameCount: Option[Int] = None

      if (update.details.isDefined) {
        val query = for { m <- samples if m.uniqueId === uniqueId } yield m.details
        detailsCount = Some(query.update(update.details.get))
      }

      if (update.name.isDefined) {
        val query = for { m <- samples if m.uniqueId === uniqueId } yield m.name
        nameCount = Some(query.update(update.name.get))
      }

      if (detailsCount.contains(0) || nameCount.contains(0)) {
        session.rollback()
        throw new ResourceNotFoundError(s"Unable to find sample $uniqueId")
      }

      samples.filter(_.uniqueId === uniqueId).first
    }
  }

  /*
   * Deletes an existing sample in the database
   * Throws if the resource was not found or if more than one was found
   */
  def deleteSample(uniqueId: UUID): String = {
    dal.db.withTransaction { implicit session =>
      val query = for { m <- samples if m.uniqueId === uniqueId } yield m
      val sampleCount = query.delete

      if (sampleCount == 1) {
        s"Successfully deleted sample $uniqueId"
      } else if (sampleCount == 0) {
        throw new ResourceNotFoundError(s"Unable to find sample $uniqueId")
      } else {
        throw new IllegalStateException(s"More than one sample found with id $uniqueId")
      }
    }
  }
}

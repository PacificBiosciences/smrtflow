package com.pacbio.secondary.smrtlink.actors

import java.util.UUID

import com.google.common.annotations.VisibleForTesting
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.{ResourceNotFoundError, UnprocessableEntityError}
import com.pacbio.common.time.{Clock, ClockProvider}
import com.pacbio.secondary.smrtlink.models._

import scala.collection.mutable
import scala.concurrent.Future

trait SampleDao {
  def getSamples(): Future[Set[Sample]]

  def getSample(uniqueId: UUID): Future[Sample]

  def createSample(login: String, create: SampleCreate): Future[Sample]

  def updateSample(uniqueId: UUID, update: SampleUpdate): Future[Sample]

  def deleteSample(uniqueId: UUID): Future[String]
}

trait SampleDaoProvider {
  val sampleDao: Singleton[SampleDao]
}

class InMemorySampleDao(clock: Clock) extends SampleDao {
  val samples: mutable.HashMap[UUID, Sample] = new mutable.HashMap

  override final def getSamples(): Future[Set[Sample]] = Future {samples.values.toSet }

  override final def getSample(uniqueId: UUID): Future[Sample] = Future {
    if (samples contains uniqueId)
      samples(uniqueId)
    else
      throw new ResourceNotFoundError(s"Unable to find resource $uniqueId")
  }

  override final def createSample(login: String, create: SampleCreate): Future[Sample] = Future {
    if (samples contains create.uniqueId) {
      throw new UnprocessableEntityError(s"Unable to create sample with uuid ${create.uniqueId}, already in use.")
    }

    val sample = Sample (
      details = create.details,
      uniqueId = create.uniqueId,
      name = create.name,
      createdBy = login,
      createdAt = clock.dateNow()
    )
    samples(sample.uniqueId) = sample
    sample
  }

  override final def updateSample(uniqueId: UUID, update: SampleUpdate): Future[Sample] = Future {
    if (!samples.contains(uniqueId)) {
      throw new ResourceNotFoundError(s"Unable to find sample $uniqueId")
    }

    val sample = samples(uniqueId).copy(
      details = update.details.getOrElse(samples(uniqueId).details),
      name = update.name.getOrElse(samples(uniqueId).name)
    )
    samples(sample.uniqueId) = sample
    sample
  }

  override final def deleteSample(uniqueId: UUID): Future[String] = Future {
    if (samples contains uniqueId) {
      samples -= uniqueId
      s"Successfully deleted sample $uniqueId"
    }
    else throw new ResourceNotFoundError(s"Unable to find sample $uniqueId")
  }

  @VisibleForTesting
  def clear(): Unit = {
    samples.clear()
  }
}

/**
  * Provides an InMemoryRunDesignDao. Concrete providers must mixin a ClockProvider.
  */
trait InMemorySampleDaoProvider extends SampleDaoProvider {
  this: ClockProvider =>

  override final val sampleDao: Singleton[SampleDao] =
    Singleton(() => new InMemorySampleDao(clock()))
}
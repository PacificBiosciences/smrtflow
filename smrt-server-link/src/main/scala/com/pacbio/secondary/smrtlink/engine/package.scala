package com.pacbio.secondary.smrtlink

import java.net.InetAddress
import java.util.UUID

import akka.actor.ActorRef
import akka.util.Timeout
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, InvalidJobOptionError, JobResultWriter}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}


/**
  * Created by mkocher on 8/16/17.
  */
package object engine {

  trait ServiceCoreJobModel extends LazyLogging {
    type Out
    val jobTypeId: JobTypeId

    // This should be rethought
    def host = InetAddress.getLocalHost.getHostName

    /**
      * The Service Job has access to the DAO, but should not update or mutate the state of the current job (or any
      * other job). The ServiceRunner and EngineWorker actor will handle updating the state of the job.
      *
      * At the job level, the job is responsible for importing any data back into the system and sending
      * "Status" update events.
      *
      * @param resources Resources for the Job to use (e.g., job id, root path)
      * @param resultsWriter Writer to write to job stdout and stderr
      * @param dao interface to the DB. See above comments on suggested use and responsibility
      * @return
      */
    def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao): Either[ResultFailed, Out]

  }

  trait ServiceJobOptions {
    val jobTypeId: JobTypeId
    val projectId: Int
    def toJob: ServiceCoreJob
    def validate(): Option[InvalidJobOptionError]
  }


  abstract class ServiceCoreJob(opts: ServiceJobOptions) extends ServiceCoreJobModel {
    // sugar
    val jobTypeId = opts.jobTypeId
  }

}

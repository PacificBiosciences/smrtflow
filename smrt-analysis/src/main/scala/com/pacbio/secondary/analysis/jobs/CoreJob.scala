package com.pacbio.secondary.analysis.jobs

import java.net.InetAddress
import java.util.UUID
import com.pacbio.secondary.analysis.jobs.JobModels._
import org.joda.time.{DateTime => JodaDateTime}
import scala.concurrent.{ExecutionContext, Future}
import com.typesafe.scalalogging.LazyLogging

/**
 * This is the fundamental generic PacBio "jobOptions" interface for
 * doing any computational work (e.g., importing, data transfer, secondary jobOptions,
 * scanning "dropbox" for references)
 *
 * Perhaps "pipeline" or "task" would be better? There's some naming inconsistencies here
 *
 * The required output interface is generating a datastore.json and task-report.json
 * (This is currently how pbsmrtpipe works)
 *
 * To Sort out (Trying to keep the tasks/jobs decoupled from the Services)
 * - jobOptions 'state' with a fsm
 * - naming
 * - consistent interface output
 * - progress URL status updating mechanism
 * - heartbeat URL status updating mechanism
 * - DataSets in the datastore.json are automatically imported?
 * - If the task/jobOptions succeeds, but at the execution layer fails does this fail the jobOptions?
 */
trait CoreJobModel extends LazyLogging{
  type Out
  val jobTypeId: JobTypeId

  def run(job: JobResourceBase, resultsWriter: JobResultWriter): Either[ResultFailed, Out]

  def host = InetAddress.getLocalHost.getHostName
}

abstract class BaseCoreJob(opts: BaseJobOptions) extends CoreJobModel {

}

case class InvalidJobOptionError(msg: String) extends Exception(msg)

// Core Job container. UUID is the globally unique id to the job
case class CoreJob(uuid: UUID, jobOptions: BaseJobOptions)


trait BaseJobOptions {
  def toJob: BaseCoreJob

  // Validation of Job Options
  def validate: Option[InvalidJobOptionError] = None
}
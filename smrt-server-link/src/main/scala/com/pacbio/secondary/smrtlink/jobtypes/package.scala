package com.pacbio.secondary.smrtlink

import java.net.InetAddress

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{EngineJob, JobResourceBase, JobTypeId, JobTypeIds, ResultFailed}
import com.pacbio.secondary.smrtlink.analysis.jobs.{InvalidJobOptionError, JobResultWriter}
import com.pacbio.secondary.smrtlink.jsonprotocols.{ServiceJobTypeJsonProtocols, SmrtLinkJsonProtocols}
// as a temporary workaround

import com.typesafe.scalalogging.LazyLogging
import spray.json._

/**
  * Created by mkocher on 8/17/17.
  */
package object jobtypes {

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
      * @param resources Resources for the Job to use (e.g., job id, root path) This needs to be expanded to include the system config.
      *                  This be renamed for clarity
      * @param resultsWriter Writer to write to job stdout and stderr
      * @param dao interface to the DB. See above comments on suggested use and responsibility
      * @return
      */
    def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao): Either[ResultFailed, Out]

  }

  trait ServiceJobOptions {
    // This metadata will be used when creating an instance of a EngineJob
    val name: Option[String]
    val description: Option[String]
    val projectId: Option[Int]

    // This needs to be defined at the job option level to be a globally unique type.
    val jobTypeId: JobTypeId
    def toJob(): ServiceCoreJob

    /**
      * The probably needs to the Dao and SMRT Link System Config passed in.
      * This exposes a lot of surface area.
      *
      * Does this also need UserRecord passed in?
      *
      * Validate the Options (and make sure they're consistent within the system config if necessary)
      * @return
      */
    def validate(): Option[InvalidJobOptionError]
  }


  abstract class ServiceCoreJob(opts: ServiceJobOptions) extends ServiceCoreJobModel {
    // sugar
    val jobTypeId = opts.jobTypeId
  }


  trait Converters {
    /**
      * Load the JSON Settings from an Engine job and create the companion ServiceJobOption
      * instance.
      *
      * @param engineJob EngineJob
      * @tparam T ServiceJobOptions
      * @return
      */
    def convertEngineToOptions[T >: ServiceJobOptions](engineJob: EngineJob): T = {

      import SmrtLinkJsonProtocols._
      import ServiceJobTypeJsonProtocols._

      val jx = engineJob.jsonSettings.parseJson

      JobTypeId(engineJob.jobTypeId) match {
        case JobTypeIds.HELLO_WORLD => jx.convertTo[HelloWorldJobOptions]
        case JobTypeIds.DB_BACKUP => jx.convertTo[DbBackUpJobOptions]
        case JobTypeIds.DELETE_DATASETS => jx.convertTo[DeleteDataSetJobOptions]
        case JobTypeIds.EXPORT_DATASETS => jx.convertTo[ExportDataSetsJobOptions]
        case JobTypeIds.CONVERT_FASTA_BARCODES => jx.convertTo[ImportBarcodeFastaJobOptions]
        case JobTypeIds.IMPORT_DATASET => jx.convertTo[ImportDataSetJobOptions]
        case JobTypeIds.CONVERT_FASTA_REFERENCE => jx.convertTo[ImportFastaJobOptions]
        case JobTypeIds.MERGE_DATASETS => jx.convertTo[MergeDataSetJobOptions]
        case JobTypeIds.MOCK_PBSMRTPIPE => jx.convertTo[MockPbsmrtpipeJobOptions]
        case JobTypeIds.PBSMRTPIPE => jx.convertTo[PbsmrtpipeJobOptions]
      }
    }
  }
  object Converters extends Converters


}

package com.pacbio.secondary.smrtlink.actors

import java.nio.file.{Path, Paths}
import java.security.MessageDigest
import java.util.UUID

import akka.actor.{Props, ActorRef, ActorLogging}
import com.pacbio.common.actors.{PacBioActor, ActorRefFactoryProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.secondary.analysis.converters.ReferenceInfoConverter
import com.pacbio.secondary.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.analysis.engine.CommonMessages.{ImportDataStoreFileByJobId, ImportDataStoreFile, UpdateJobStatus}
import com.pacbio.secondary.analysis.jobs.JobModels.DataStoreFile
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, CoreJob}
import com.pacbio.secondary.smrtlink.models.{Converters, ReferenceServiceDataSet, EngineJobEntryPointRecord, ProjectRequest, ProjectUserRequest}
import org.joda.time.{DateTime => JodaDateTime}


object MessageTypes {
  abstract class ProjectMessage

  abstract class JobMessage

  abstract class DataSetMessage

  abstract class DataStoreMessage
}

object JobsDaoActor {
  import MessageTypes._

  // Project
  case object GetProjects extends ProjectMessage
  case class GetProjectById(projId: Int) extends ProjectMessage
  case class CreateProject(opts: ProjectRequest) extends ProjectMessage
  case class UpdateProject(projId: Int, opts: ProjectRequest) extends ProjectMessage
  case class GetProjectUsers(projId: Int) extends ProjectMessage
  case class AddProjectUser(projId: Int, user: ProjectUserRequest) extends ProjectMessage
  case class DeleteProjectUser(projId: Int, user: String) extends ProjectMessage
  case class GetDatasetsByProject(projId: Int) extends ProjectMessage
  case class GetUserProjects(login: String)
  case class GetUserProjectsDatasets(user: String) extends ProjectMessage
  case class SetProjectForDatasetId(dsId: Int, projId: Int) extends ProjectMessage
  case class SetProjectForDatasetUuid(dsId: UUID, projId: Int) extends ProjectMessage

  // Job
  case object GetAllJobs extends JobMessage

  case class GetJobById(jobId: Int) extends JobMessage

  case class GetJobByUUID(jobId: UUID) extends JobMessage

  case class GetJobsByJobType(jobTypeId: String) extends JobMessage

  case class GetJobEventsByJobId(jobId: Int) extends JobMessage

  case class UpdateJobState(jobId: Int, state: AnalysisJobStates.JobStates, message: String) extends JobMessage

  case class CreateJobType(uuid: UUID, name: String, comment: String, jobTypeId: String,
                           coreJob: CoreJob, engineEntryPoints: Option[Seq[EngineJobEntryPointRecord]] = None,
                           jsonSettings: String, createdBy: Option[String]) extends JobMessage


  // Get all DataSet Entry Points
  case class GetEngineJobEntryPoints(jobId: Int)

  // DataSet
  case object GetDataSetTypes extends DataSetMessage

  case class GetDataSetTypeById(i: String) extends DataSetMessage

  // Get all DataSet Metadata records
  case class GetDataSetMetaById(i: Int) extends DataSetMessage

  case class GetDataSetMetaByUUID(uuid: UUID) extends DataSetMessage

  // DS Subreads
  case class GetSubreadDataSets(limit: Int) extends DataSetMessage

  case class GetSubreadDataSetById(i: Int) extends DataSetMessage

  case class GetSubreadDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetSubreadDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetSubreadDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  // DS Reference
  case class GetReferenceDataSets(limit: Int) extends DataSetMessage

  case class GetReferenceDataSetById(i: Int) extends DataSetMessage

  case class GetReferenceDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetReferenceDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetReferenceDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  // Alignment
  case class GetAlignmentDataSetById(i: Int) extends DataSetMessage

  case class GetAlignmentDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetAlignmentDataSets(limit: Int)

  // Hdf Subreads
  case class GetHdfSubreadDataSetById(i: Int) extends DataSetMessage

  case class GetHdfSubreadDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetHdfSubreadDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetHdfSubreadDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  case class GetHdfSubreadDataSets(limit: Int) extends DataSetMessage

  // CCS Subreads
  case class GetCCSSubreadDataSetsById(i: Int) extends DataSetMessage

  case class GetCCSSubreadDataSetsByUUID(uuid: UUID) extends DataSetMessage

  case class GetCCSSubreadDataSets(limit: Int) extends DataSetMessage

  // Barcode DataSets
  case class GetBarcodeDataSets(limit: Int) extends DataSetMessage

  case class GetBarcodeDataSetsById(i: Int) extends DataSetMessage

  case class GetBarcodeDataSetsByUUID(uuid: UUID) extends DataSetMessage

  case class GetBarcodeDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetBarcodeDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage


  // Import a Reference Dataset
  case class ImportReferenceDataSet(ds: ReferenceServiceDataSet) extends DataSetMessage

  case class ImportReferenceDataSetFromFile(path: String) extends DataSetMessage

  // This should probably be a different actor
  // Convert a Reference to a Dataset and Import
  case class ConvertReferenceInfoToDataset(path: String, dsPath: Path) extends DataSetMessage

  case class ImportSubreadDataSetFromFile(path: String) extends DataSetMessage


  // DataStore Files
  case class GetDataStoreFileByUUID(uuid: UUID) extends DataStoreMessage

  case class GetDataStoreServiceFilesByJobId(i: Int) extends DataStoreMessage

  case class GetDataStoreReportFileByJobId(jobId: Int) extends DataStoreMessage

  case class GetDataStoreReportByUUID(uuid: UUID) extends DataStoreMessage

  case class GetDataStoreFiles(limit: Int = 1000) extends DataStoreMessage

  case class GetDataStoreFilesByJobId(i: Int) extends DataStoreMessage

  case class GetDataStoreFilesByJobUUID(i: UUID) extends DataStoreMessage
}

class JobsDaoActor(dao: JobsDao) extends PacBioActor with ActorLogging {

  import JobsDaoActor._

  def toMd5(text: String): String =
    MessageDigest.getInstance("MD5").digest(text.getBytes).map("%02x".format(_)).mkString

  def toE(msg: String) = throw new ResourceNotFoundError(msg)

  def receive: Receive = {

    case GetProjects => pipeWith(dao.getProjects(1000))
    case GetProjectById(projId: Int) => pipeWith {
      dao.getProjectById(projId).map(_.getOrElse(toE(s"Unable to find project $projId")))
    }
    case CreateProject(opts: ProjectRequest) => pipeWith(dao.createProject(opts))
    case UpdateProject(projId: Int, opts: ProjectRequest) => pipeWith {
      dao.updateProject(projId, opts).map(_.getOrElse(toE(s"Unable to find project $projId")))
    }
    case GetProjectUsers(projId: Int) =>
      pipeWith(dao.getProjectUsers(projId))
    case AddProjectUser(projId: Int, user: ProjectUserRequest) =>
      pipeWith(dao.addProjectUser(projId, user))
    case DeleteProjectUser(projId: Int, user: String) =>
      pipeWith(dao.deleteProjectUser(projId, user))
    case GetDatasetsByProject(projId: Int) =>
      pipeWith(dao.getDatasetsByProject(projId))
    case GetUserProjects(login: String) =>
      pipeWith(dao.getUserProjects(login))
    case GetUserProjectsDatasets(user: String) =>
      pipeWith(dao.getUserProjectsDatasets(user))
    case SetProjectForDatasetId(dsId: Int, projId: Int) =>
      pipeWith(dao.setProjectForDatasetId(dsId, projId))
    case SetProjectForDatasetUuid(dsId: UUID, projId: Int) =>
      pipeWith(dao.setProjectForDatasetUuid(dsId, projId))


    case GetAllJobs => respondWith(dao.getJobs(1000))

    case GetJobsByJobType(jobTypeId) => respondWith(dao.getJobsByTypeId(jobTypeId))

    case GetJobById(jobId: Int) => respondWith {
      dao.getJobById(jobId).getOrElse(toE(s"Unable to find JobId $jobId"))
    }

    case GetJobByUUID(uuid) => respondWith {
      dao.getJobByUUID(uuid).getOrElse(toE(s"Unable to find job ${uuid.toString}"))
    }

    case GetJobEventsByJobId(jobId: Int) => respondWith(dao.getJobEventsByJobId(jobId))

    case GetDataSetMetaById(i: Int) => respondWith {
      dao.getDataSetById(i).getOrElse(toE(s"Unable to find dataset $i"))
    }

    case GetDataSetMetaByUUID(i: UUID) => respondWith {
      dao.getDataSetByUUID(i).getOrElse(toE(s"Unable to find dataset $i"))
    }

    // DataSet Types
    case GetDataSetTypes => respondWith(dao.getDataSetTypes)

    case GetDataSetTypeById(n: String) => respondWith {
      dao.getDataSetTypeById(n).getOrElse(toE(s"Unable to find dataset type '$n"))
    }

    // Get Subreads
    case GetSubreadDataSets(limit: Int) => respondWith(dao.getSubreadDataSets(limit))

    case GetSubreadDataSetById(n: Int) => respondWith {
      dao.getSubreadDataSetById(n).getOrElse(toE(s"Unable to find subread dataset '$n"))
    }

    case GetSubreadDataSetByUUID(uuid: UUID) => respondWith {
      dao.getSubreadDataSetByUUID(uuid).getOrElse(toE(s"Unable to find subread dataset '$uuid"))
    }

    case GetSubreadDataSetDetailsById(n: Int) => respondWith {
      dao.getSubreadDataSetDetailsById(n).getOrElse(toE(s"Unable to find subread dataset '$n"))
    }

    case GetSubreadDataSetDetailsByUUID(uuid: UUID) => respondWith {
      dao.getSubreadDataSetDetailsByUUID(uuid).getOrElse(toE(s"Unable to find subread dataset ${uuid.toString}"))
    }

    // Get References
    case GetReferenceDataSets(limit: Int) => respondWith(dao.getReferenceDataSets(limit))

    case GetReferenceDataSetById(id: Int) => respondWith {
      dao.getReferenceDataSetById(id).getOrElse(toE(s"Unable to find reference dataset '$id"))
    }

    case GetReferenceDataSetByUUID(uuid: UUID) => respondWith {
      dao.getReferenceDataSetByUUID(uuid).getOrElse(toE(s"Unable to find reference dataset '$uuid"))
    }

    case GetReferenceDataSetDetailsById(id: Int) => respondWith {
      dao.getReferenceDataSetDetailsById(id).getOrElse(toE(s"Unable to find reference details dataset '$id"))
    }

    case GetReferenceDataSetDetailsByUUID(id: UUID) => respondWith {
      dao.getReferenceDataSetDetailsByUUID(id).getOrElse(toE(s"Unable to find reference details dataset '$id"))
    }

    case ImportReferenceDataSet(ds: ReferenceServiceDataSet) => respondWith {
      log.debug("creating reference dataset")
      dao.insertReferenceDataSet(ds)
    }

    case ImportReferenceDataSetFromFile(path: String) => respondWith {
      // FIXME. This should be removed
      val createdAt = JodaDateTime.now()
      val r = DataSetLoader.loadReferenceSet(Paths.get(path))
      val uuid = UUID.fromString(r.getUniqueId)
      val ds = ReferenceServiceDataSet(
        -99,
        uuid,
        r.getName,
        path,
        createdAt, createdAt,
        r.getDataSetMetadata.getNumRecords,
        r.getDataSetMetadata.getTotalLength,
        "0.5.0",
        "reference comments",
        "reference-tags",
        toMd5(uuid.toString),
        1,
        1,
        1,
        r.getDataSetMetadata.getPloidy,
        r.getDataSetMetadata.getOrganism)

      dao.insertReferenceDataSet(ds)
    }

    // get Alignments
    case GetAlignmentDataSets(limit: Int) => respondWith(dao.getAlignmentDataSets(limit))

    case GetAlignmentDataSetById(n: Int) => respondWith {
      dao.getAlignmentDataSetById(n).getOrElse(toE(s"Unable to find Alignment dataset '$n"))
    }

    case GetAlignmentDataSetByUUID(uuid: UUID) => respondWith {
      dao.getAlignmentDataSetByUUID(uuid).getOrElse(toE(s"Unable to find Alignment dataset '$uuid"))
    }

    // Get HDF Subreads
    case GetHdfSubreadDataSets(limit: Int) => respondWith(dao.getHdfDataSets(limit))

    case GetHdfSubreadDataSetById(n: Int) => respondWith {
      dao.getHdfDataSetById(n).getOrElse(toE(s"Unable to find Hdf subread dataset '$n"))
    }

    case GetHdfSubreadDataSetByUUID(uuid: UUID) => respondWith {
      dao.getHdfDataSetByUUID(uuid).getOrElse(toE(s"Unable to find Hdf subread dataset '$uuid"))
    }

    case GetHdfSubreadDataSetDetailsById(n: Int) => respondWith {
      dao.getHdfDataSetDetailsById(n).getOrElse(toE(s"Unable to find Hdf subread dataset '$n"))
    }

    case GetHdfSubreadDataSetDetailsByUUID(uuid: UUID) => respondWith {
      dao.getHdfDataSetDetailsByUUID(uuid).getOrElse(toE(s"Unable to find Hdf subread dataset '$uuid"))
    }

    // Get CCS Subreads
    case GetCCSSubreadDataSets(limit: Int) => respondWith(dao.getCCSDataSets(limit))

    case GetCCSSubreadDataSetsById(n: Int) => respondWith {
      dao.getCCSDataSetById(n).getOrElse(toE(s"Unable to find Hdf subread dataset '$n"))
    }

    case GetCCSSubreadDataSetsByUUID(uuid: UUID) => respondWith {
      dao.getCCSDataSetByUUID(uuid).getOrElse(toE(s"Unable to find Hdf subread dataset '$uuid"))
    }

    // Get Barcodes
    case GetBarcodeDataSets(limit: Int) => respondWith(dao.getBarcodeDataSets(limit))

    case GetBarcodeDataSetsById(n: Int) => respondWith {
      dao.getBarcodeDataSetById(n).getOrElse(toE(s"Unable to find Barcode dataset '$n"))
    }

    case GetBarcodeDataSetsByUUID(uuid: UUID) => respondWith {
      dao.getBarcodeDataSetByUUID(uuid).getOrElse(toE(s"Unable to find Barcode dataset '$uuid"))
    }

    case GetBarcodeDataSetDetailsByUUID(uuid) => respondWith {
      dao.getBarcodeDataSetByUUID(uuid).getOrElse(toE(s"Unable to find Barcode dataset Details for '$uuid"))
    }

    case GetBarcodeDataSetDetailsById(i) => respondWith {
      dao.getBarcodeDataSetById(i).getOrElse(toE(s"Unable to find Barcode dataset Details for '$i"))
    }

    case ConvertReferenceInfoToDataset(path: String, dsPath: Path) => respondWith {
      log.info(s"Converting reference.info.xml to dataset XML $path")
      ReferenceInfoConverter.convertReferenceInfoXMLToDataset(path, dsPath) match {
        case Right(ds) => s"Successfully converted reference.info.xml to dataset and imported ${ds.dataset.metadata.uuid} path:$dsPath"
        case Left(e) => throw new Exception(s"DataSetConversionError: Unable to convert and import $path. Error ${e.msg}")
      }
    }

    case ImportSubreadDataSetFromFile(path: String) => respondWith {
      log.info(s"Importing subread dataset from $path")
      //val d = SubreadDataset.loadFrom(Paths.get(path).toUri)
      val pathP = Paths.get(path)
      val userId = 1
      val projectId = 1
      val jobId = 1
      val d = DataSetLoader.loadSubreadSet(Paths.get(path))
      val serviceDataSet = Converters.convert(d, pathP, userId, jobId, projectId)
      dao.insertSubreadDataSet(serviceDataSet)
    }

    // DataStore Files
    case GetDataStoreFiles(limit) => respondWith(dao.getDataStoreFiles2)

    case GetDataStoreFileByUUID(uuid: UUID) => respondWith {
      dao.getDataStoreFileByUUID2(uuid).getOrElse(toE(s"Unable to find DataStoreFile ${uuid.toString}"))
    }

    case GetDataStoreFilesByJobId(jobId) => respondWith(dao.getDataStoreFilesByJobId(jobId))

    case GetDataStoreServiceFilesByJobId(jobId: Int) => respondWith(dao.getDataStoreServiceFilesByJobId(jobId))

    // Reports
    case GetDataStoreReportFileByJobId(jobId: Int) => respondWith(dao.getDataStoreReportFilesByJobId(jobId))

    case GetDataStoreReportByUUID(reportUUID: UUID) => respondWith {
      dao.getDataStoreReportByUUID(reportUUID).getOrElse(toE(s"Unable to find report ${reportUUID.toString}"))
    }

    case GetDataStoreFilesByJobUUID(id) => respondWith(dao.getDataStoreFilesByJobUUID(id))

    case ImportDataStoreFile(dsf: DataStoreFile, jobId: UUID) => respondWith(dao.insertDataStoreFileByUUID(dsf, jobId))

    case ImportDataStoreFileByJobId(dsf: DataStoreFile, jobId) => respondWith(dao.insertDataStoreFileById(dsf, jobId))

    case CreateJobType(uuid, name, pipelineId, jobTypeId, coreJob, entryPointRecords, jsonSettings, createdBy) =>
      respondWith(dao.createJob(uuid, name, pipelineId, jobTypeId, coreJob, entryPointRecords, jsonSettings, createdBy))

    case UpdateJobState(jobId: Int, state: AnalysisJobStates.JobStates, message: String) =>
      respondWith(dao.updateJobState(jobId, state, message))

    case GetEngineJobEntryPoints(jobId) => respondWith(dao.getJobEntryPoints(jobId))

    // Need to consolidate this
    case UpdateJobStatus(uuid, state) =>
      respondWith(dao.updateJobStateByUUID(uuid, state, s"Updating job id $uuid to $state"))

    // Testing/Debugging messages
    case "example-test-message" => respondWith("Successfully got example-test-message")

    case x => log.error(s"Unhandled message $x to database actor.")
  }
}

trait JobsDaoActorProvider {
  this: ActorRefFactoryProvider with JobsDaoProvider =>

  val jobsDaoActor: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[JobsDaoActor], jobsDao())))
      .bindToSet(JobListeners)
}

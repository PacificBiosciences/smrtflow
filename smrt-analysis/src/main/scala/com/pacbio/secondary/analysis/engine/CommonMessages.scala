package com.pacbio.secondary.analysis.engine

import java.nio.file.Path
import java.util.UUID

import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, CoreJob}
import com.pacbio.secondary.analysis.jobs.JobModels.{DataStoreFile, JobResult, RunnableJob, ImportAble}

object CommonMessages {

  // New Job model
  case class RunJob(job: CoreJob, path: Path)

  // Add a new Job
  case class AddNewJob(job: CoreJob)

  // Not sure if this is the best model for doing this
  sealed trait WorkerType
  case object QuickWorkType extends WorkerType
  case object StandardWorkType extends WorkerType

  case class UpdateJobCompletedResult(result: JobResult, workerType: WorkerType)

  // Some endpoints were originally implemented to return string-typed
  // responses, but the smrt-link client has been sending an Accept:
  // application/json header for all requests.  With that request
  // header, the server was responding with a 406 for the
  // string-response-typed endpoints.  Those string-returning endpoints
  // were mostly returning success/failure messages, so they can use
  // this class instead to return a json-typed message response.
  case class MessageResponse(message: String)

  // General Successful message. Intended to be used in DAO layer
  case class SuccessMessage(message: String)

  // General Failed Message
  case class FailedMessage(message: String)

  case object HasNextRunnableJobWithId

  case object CheckForRunnableJob

  case object AllJobsCompleted

  case class UpdateJobStatus(uuid: UUID, state: AnalysisJobStates.JobStates)

  case class GetAllJobs(limit: Int)

  case object GetSystemJobSummary

  case class GetJobStatusByUUID(uuid: UUID)
  case class GetJobStatusById(i: Int)

  // DataSet Related Messages
  case class PacBioImportDataSet(datum: ImportAble, jobId: UUID)
  case class ImportDataStoreFile(dataStoreFile: DataStoreFile, jobUUID: UUID)
  case class ImportDataStoreFileByJobId(dataStoreFile: DataStoreFile, jobId: Int)
  case class DeleteDataStoreFile(uuid: UUID)

}

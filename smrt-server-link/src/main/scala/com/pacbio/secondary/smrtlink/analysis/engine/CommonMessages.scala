package com.pacbio.secondary.smrtlink.analysis.engine

import java.nio.file.Path
import java.util.UUID

import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, CoreJob}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{DataStoreFile, ImportAble, JobResult, RunnableJob}

object CommonMessages {

  // New Job model
  case class RunJob(job: CoreJob, path: Path)

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

  case class GetAllJobs(limit: Int)

  case object GetSystemJobSummary

  case class GetJobStatusById(i: IdAble)

  case class UpdateJobState(jobId: IdAble, state: AnalysisJobStates.JobStates, message: String, errorMessage: Option[String])

  // DataSet Related Messages
  case class ImportDataStoreFileByJobId(dataStoreFile: DataStoreFile, jobId: IdAble)
  case class DeleteDataStoreFile(uuid: UUID)

}

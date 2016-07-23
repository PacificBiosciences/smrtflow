package com.pacbio.secondaryinternal.client

import java.io.IOError
import java.net.URL
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}

import akka.actor.ActorSystem

import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes.DataSetMetaType
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondary.smrtlink.models.EngineJobEntryPoint
import com.pacbio.secondary.smrtserver.client.AnalysisServiceAccessLayer
import com.pacbio.secondaryinternal.{IOUtils, JobResolvers}
import com.pacbio.secondaryinternal.models.{ResolvedCondition, ResolvedConditions, _}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

import spray.http._
import spray.client.pipelining._


class InternalAnalysisServiceClient(baseUrl: URL)(implicit actorSystem: ActorSystem)
    extends AnalysisServiceAccessLayer(baseUrl)(actorSystem) with LazyLogging {

  val conditionJobTypeId = "conditions"

  val conditionJobURL = toUrl(s"${ServiceEndpoints.ROOT_JOBS}/$conditionJobTypeId")

  def failJobIfNotSuccessful(job: EngineJob): Future[EngineJob] =
    if (job.state == AnalysisJobStates.SUCCESSFUL) Future { job }
    else Future.failed(throw new Exception(s"Job ${job.id} was not successful ${job.state}. Unable to process conditions"))

  def getEntryPointBy(eps: Seq[EngineJobEntryPoint], datasetMetaType: DataSetMetaType): Future[UUID] = {
    eps.find(_.datasetType == datasetMetaType.dsId) match {
      case Some(x) => Future {
        x.datasetUUID
      }
      case _ => Future.failed(throw new Exception(s"Failed resolve Entry Point type $datasetMetaType"))
    }
  }

  def validatePath(path: Path, message: String): Future[Path] = {
    if (Files.exists(path)) Future {path}
    else Future.failed(throw new Exception(s"$message Unable to find $path"))
  }

  /**
    * Converts the raw CSV and resolves AlignmentSets paths from job ids
    *
    * @param record
    * @return
    */
  def resolveConditionRecord(record: ServiceConditionCsvPipeline): Future[ReseqConditions] = {
    logger.info(s"Converting $record")

    val sx = scala.io.Source.fromString(record.csvContents)
    //println(sx)
    logger.debug(s"Loading raw CSV content ${record.csvContents}")

    val cs = IOUtils.parseConditionCsv(sx)
    logger.debug(s"Parsed conditions $cs")

    /**
      * This needs to have a more robust implementation to get the AlignmentSet
      *
      * Resolve Entry points looks for a SubreadSet and ReferenceSet
      * And will validate paths of resolved files.
      *
      * @param sc Service Condition
      * @return
      */
    def resolve(sc: ServiceCondition): Future[ReseqCondition] = {
      for {
        job <- getJobById(sc.jobId)
        sjob <- failJobIfNotSuccessful(job)
        alignmentSetPath <- JobResolvers.resolveAlignmentSet(this, sc.jobId) // FIXME
        entryPoints <- getAnalysisJobEntryPoints(sc.jobId)
        subreadSetUUID <- getEntryPointBy(entryPoints, DataSetMetaTypes.Subread)
        referenceSetUUID <- getEntryPointBy(entryPoints, DataSetMetaTypes.Reference)
        subreadSetMetadata <- getDataSetByUuid(subreadSetUUID)
        referenceSetMetadata <- getDataSetByUuid(referenceSetUUID)
        ssetPath <- validatePath(Paths.get(subreadSetMetadata.path), s"SubreadSet path for Job ${job.id}")
        rsetPath <- validatePath(Paths.get(referenceSetMetadata.path), s"ReferenceSet path for job ${job.id}")
      } yield ReseqCondition(sc.id, ssetPath, alignmentSetPath, rsetPath)
    }

    // Do them in parallel
    val fxs = Future.sequence(cs.map(resolve))

    // A few sanity tests for making sure the system is up and pipeline id is valid
    val fx = for {
      _ <- getStatus
      //pipeline <- getPipelineTemplate(record.pipelineId) // This doesn't work
      resolvedConditions <- fxs
    } yield ReseqConditions(record.pipelineId, resolvedConditions)

    fx
  }

  def resolvedJobConditionsTo(p: ResolvedConditionPipeline): ResolvedConditions = {
    val cs = p.conditions.map(x => ResolvedCondition(x.id, FileTypes.DS_ALIGNMENTS.fileTypeId, Seq(x.path)))
    ResolvedConditions(p.pipelineId, cs)
  }


}

package com.pacbio.secondaryinternal.client

import java.net.URL
import java.nio.file.Paths
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


class InternalAnalysisServiceClient(baseUrl: URL)(implicit actorSystem: ActorSystem)
    extends AnalysisServiceAccessLayer(baseUrl)(actorSystem) with LazyLogging {

  val conditionJobTypeId = "conditions"

  val conditionJobURL = toUrl(s"${ServiceEndpoints.ROOT_JOBS}/$conditionJobTypeId")


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

    def toUrl(host: String, port: Int) = new URL(s"http://$host:$port")

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


    /**
      * This needs to have a more robust implementation
      *
      * - Resolve Entry points looks for a SubreadSet and ReferenceSet
      * - Does not valid resolved paths
      *
      * @param sc Service Condition
      * @return
      */
    def resolve(sc: ServiceCondition): Future[ReseqCondition] = {
      for {
        job <- getJobById(sc.jobId)
        sjob <- failJobIfNotSuccessful(job)
        alignmentSetPath <- JobResolvers.resolveAlignmentSet(this, sc.jobId)
        entryPoints <- getAnalysisJobEntryPoints(sc.jobId)
        subreadSetUUID <- getEntryPointBy(entryPoints, DataSetMetaTypes.Subread)
        referenceSetUUID <- getEntryPointBy(entryPoints, DataSetMetaTypes.Reference)
        subreadSetMetadata <- getDataSetByUuid(subreadSetUUID)
        referenceSetMetadata <- getDataSetByUuid(referenceSetUUID)
      } yield ReseqCondition(sc.id, Paths.get(subreadSetMetadata.path), alignmentSetPath, Paths.get(referenceSetMetadata.path))
    }

    // Do them in parallel
    val fxs = Future.sequence(cs.map(resolve))

    // A few sanity tests for making sure the system is up and pipeline id is valid
    val fx = for {
      _ <- getStatus
      pipeline <- getPipelineTemplate(record.pipelineId)
      resolvedConditions <- fxs
    } yield ReseqConditions(record.pipelineId, resolvedConditions)

    fx
  }

  def resolvedJobConditionsTo(p: ResolvedConditionPipeline): ResolvedConditions = {
    ResolvedConditions(p.pipelineId, p.conditions.map(x => ResolvedCondition(x.id, FileTypes.DS_ALIGNMENTS.fileTypeId, Seq(x.path))))
  }

  // FIXME
  def submitReseqConditions(r: ReseqConditions): Future[EngineJob] = {
    Future {
      EngineJob(-1, UUID.randomUUID(), "Job Name", "", JodaDateTime.now(), JodaDateTime.now(), AnalysisJobStates.UNKNOWN, "conditions", "", "{}", None)
    }
  }


}

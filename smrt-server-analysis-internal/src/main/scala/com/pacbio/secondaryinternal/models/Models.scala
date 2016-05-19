package com.pacbio.secondaryinternal.models

import java.util.UUID
import java.nio.file.Path

import com.pacbio.secondary.analysis.constants.FileTypes

object Models {}


case class JobResourceError(message: String)
case class JobResource(id: Int, systemId: String, path: String)

// Portal/Martin job resource
case class PortalResource(id: String, baseJobDir: String)
// Portal/Martin Job Resolver
case class PortalResolver(systemId: String, path: String)

// SMRTLink Service Resource
// FIXME. remove the jobRoot and make calls to the services to do *everything*.
// Working around the API by using the filesystem creates two interfaces that
// that need to be maintained!
case class SmrtLinkServerResource(id: String, name: String, host: String, port: Int, jobRoot: String)

// Minimal Smrt link job model as a starting point. Add job state when a proper client API exists
case class SmrtLinkJob(id: Int, path: String)

// RunCode
case class RuncodeResolveError(runcode: String, message: String)
case class RuncodeResource(runcode: String, expId: Int, path: String, windowsPath: String, datasetUUID: UUID)

// Starting Point for SubreadSet + Internal Metadata
// Run code has the form 3150005-0033, exp id has 3150005, Exp has many runs
case class InternalSubreadSet(runcode: String, expId: Int, path: String, datasetUUID: UUID) {
  def asRuncodeResource =
    RuncodeResource(runcode, expId, path,  "\\" + path.replace("/", "\\"), datasetUUID)
}

// Thin container for resolving References by 'id', (e.g., 'lambdaNeb')
case class ReferenceSetResource(id: String, path: String)

// Analysis Conditions
// TODO. Add file-type-id correctly
case class AnalysisCondition(id: String, path: Seq[Path]) {
  def fileType = FileTypes.DS_ALIGNMENTS
}

// CSV -> ServiceCondition
case class ServiceCondition(id: String, host: String, port: Int, jobId: Int)
// Not clear that the host, port and job Id should be passed as the fundamental unit to the Condition JSON
case class ResolvedJobCondition(id: String, host: String, port: Int, jobId: Int, path:Path)

// This is the interface to the pbinternal2/pysiv2 code. Need to rethink this. file type is always AlignmentSet
case class ResolvedCondition(id: String, file_type_id: String, files: Seq[Path])
/// This is the fundamental file that is passed to tools as an entry-point
case class ResolvedConditions(pipelineId: String, conditions: Seq[ResolvedCondition])

// Pipeline + Raw CSV contents
case class ServiceConditionCsvPipeline(pipelineId: String, csvContents: String)

case class ResolvedConditionPipeline(pipelineId: String, conditions: Seq[ResolvedJobCondition])
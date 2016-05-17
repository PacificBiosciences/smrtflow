package com.pacbio.secondaryinternal.models

import java.util.UUID

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

package com.pacbio.secondaryinternal

import java.util.UUID

import com.pacbio.common.models.BaseJsonProtocol
import spray.json._
import com.pacbio.secondaryinternal.models._

/**
  * Created by mkocher on 12/19/15.
  */
trait InternalAnalysisJsonProcotols extends BaseJsonProtocol{

  implicit val jobResourceErrorFormat = jsonFormat1(JobResourceError)
  implicit val jobResourceFormat = jsonFormat3(JobResource)
  implicit val portalJobResolverFormat = jsonFormat2(PortalResolver)
  implicit val portalResourceFormat = jsonFormat2(PortalResource)

  implicit val rcResourceErrorFormat = jsonFormat2(RuncodeResolveError)
  implicit val rcResourceFormat = jsonFormat5(RuncodeResource)

  // SmrtLink Systems
  implicit val smrtLinkResourceFormat = jsonFormat5(SmrtLinkServerResource)
  implicit val smrtLinkJobFormat = jsonFormat2(SmrtLinkJob)

  // SubreadSet model + Internal Metadata
  implicit val internalSubreadSetFormat = jsonFormat4(InternalSubreadSet)

  // ReferenceSet resolver
  implicit val internalReferenceSetResourceFormat = jsonFormat2(ReferenceSetResource)
}

object InternalAnalysisJsonProcotols extends InternalAnalysisJsonProcotols

package com.pacbio.secondaryinternal

import java.util.UUID
import java.nio.file.{Path, Paths}

import spray.json._

import com.pacbio.secondary.analysis.constants.FileTypes
import FileTypes._
import com.pacbio.common.models.BaseJsonProtocol
import com.pacbio.secondaryinternal.models._


// This Should be pushed back to a common layer
trait PathJsonProtocol extends DefaultJsonProtocol {

  implicit object PathJsonFormat extends JsonFormat[Path] {
    def write(obj: Path): JsValue = JsString(obj.toString)

    def read(value: JsValue): Path = {
      value match {
        case JsString(x) => Paths.get(x)
        case _ => deserializationError("Expected File Path")
      }
    }
  }
}

// Type issues with subclasses
//trait FileTypesProcotol extends BaseJsonProtocol {
//
//  implicit object FileTypeJsonFormat extends JsonFormat[FileBaseType] {
//    def write(obj: FileBaseType): JsValue = JsString(obj.fileTypeId)
//
//    def read(value: JsValue): FileBaseType = {
//      value match {
//        case JsString(FileTypes.DS_ALIGNMENTS.fileTypeId) => FileTypes.DS_ALIGNMENTS
//        case _ => deserializationError("Only AlignmentSet FileType is Supported")
//      }
//    }
//  }
//}


/**
  * Created by mkocher on 12/19/15.
  */
trait InternalAnalysisJsonProcotols extends BaseJsonProtocol
  with PathJsonProtocol{

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

  // Analysis Conditions
  implicit val analysisConditionFormat = jsonFormat2(AnalysisCondition)

}

object InternalAnalysisJsonProcotols extends InternalAnalysisJsonProcotols

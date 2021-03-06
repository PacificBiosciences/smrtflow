package com.pacbio.secondary.smrtlink.loaders

import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.PbsmrtpipeConstants
import com.pacbio.secondary.smrtlink.models.JsonAble
import spray.json._

/**
  *
  * Created by mkocher on 9/25/15.
  */
trait ReportViewRulesResourceLoader extends BundleResourceLoader[JsonAble] {

  val ROOT_DIR_PREFIX = "report-view-rules"
  val BUNDLE_ENV_VAR = PbsmrtpipeConstants.ENV_BUNDLE_DIR
  val ENV_VAR = PbsmrtpipeConstants.ENV_PB_RULES_REPORT_VIEW_DIR

  def loadFromString(xs: String): JsonAble = {
    val jx = xs.parseJson
    jx.convertTo[JsonAble]
  }
}

object ReportViewRulesResourceLoader extends ReportViewRulesResourceLoader

package com.pacbio.secondary.smrtlink.loaders

import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.PbsmrtpipeConstants
import com.pacbio.secondary.smrtlink.models.SecondaryModels.ReportViewRule
import spray.json._


/**
 *
 * Created by mkocher on 9/25/15.
 */
trait ReportViewRulesResourceLoader extends JsonAndEnvResourceLoader[ReportViewRule] {
  val ROOT_DIR_PREFIX = "report-view-rules"

  val ENV_VAR = PbsmrtpipeConstants.ENV_PB_RULES_REPORT_VIEW_DIR

  def loadFromString(xs: String): ReportViewRule = {
    val jx = xs.parseJson
    jx.convertTo[ReportViewRule]
  }
}

object ReportViewRulesResourceLoader extends ReportViewRulesResourceLoader

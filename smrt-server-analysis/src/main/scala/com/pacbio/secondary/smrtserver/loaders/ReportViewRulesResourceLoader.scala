package com.pacbio.secondary.smrtserver.loaders

import com.pacbio.secondary.analysis.pbsmrtpipe.PbsmrtpipeConstants
import spray.json._
import com.pacbio.secondary.smrtserver.models.SecondaryModels.ReportViewRule


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

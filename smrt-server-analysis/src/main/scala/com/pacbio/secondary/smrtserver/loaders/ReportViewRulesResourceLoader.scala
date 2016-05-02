package com.pacbio.secondary.smrtserver.loaders

import spray.json._

import com.pacbio.secondary.smrtserver.models.SecondaryModels.ReportViewRule


/**
 *
 * Created by mkocher on 9/25/15.
 */
trait ReportViewRulesResourceLoader extends JsonResourceLoader[ReportViewRule]{
  var RPT_PREFIX = "report-view-rules"

  def stringTo(xs: String): ReportViewRule = {
    val jx = xs.parseJson
    jx.convertTo[ReportViewRule]
  }
  
}
object ReportViewRulesResourceLoader extends ReportViewRulesResourceLoader

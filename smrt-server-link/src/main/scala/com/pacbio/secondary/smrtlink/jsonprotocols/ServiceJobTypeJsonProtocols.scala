package com.pacbio.secondary.smrtlink.jsonprotocols

import com.pacbio.secondary.smrtlink.jobtypes._
import fommil.sjs.FamilyFormats
import spray.json.DefaultJsonProtocol

/**
  * Created by mkocher on 8/22/17.
  */
trait ServiceJobTypeJsonProtocols extends DefaultJsonProtocol with FamilyFormats{

  import SmrtLinkJsonProtocols._
  // put these here for now
  implicit val helloWorldJobOptionJsonFormat = jsonFormat4(HelloWorldJobOptions)
  implicit val dbBackUpJobOptionJsonFormat = jsonFormat5(DbBackUpJobOptions)
  implicit val deleteDataSetobOptionJsonFormat = jsonFormat7(DeleteDataSetJobOptions)

  // These are from NEW JobType Option data model

  implicit val mergeDataSetJobOptionJsonFormat = jsonFormat5(MergeDataSetJobOptions)
  //implicit val importDataSetJobOptionJsonFormat = jsonFormat5(ImportDataSetJobOptions)

  implicit val exportDataSetJobOptionJsonFormat = jsonFormat6(ExportDataSetsJobOptions)
  implicit val importBarcodeFastaJobOptionsJsonFormat = jsonFormat4(ImportBarcodeFastaJobOptions)
  implicit val importFastaJobOptionsJsonFormat = jsonFormat6(ImportFastaJobOptions)

  // Renaming workaround
  implicit val pbsmrtpipeJobOptionsJsonFormat = jsonFormat7(PbsmrtpipeJobOptions)
  implicit val mockPbsmrtpipeJobOptionsJsonFormat = jsonFormat7(MockPbsmrtpipeJobOptions)




}

object ServiceJobTypeJsonProtocols extends ServiceJobTypeJsonProtocols

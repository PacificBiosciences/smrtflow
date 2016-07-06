package db.migration

import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}

object SqliteToH2DataHolder {
  var data: Option[SqliteToH2Data] = None
}

case class SqliteToH2Data(jobEvents: Seq[(UUID, Int, String, String, JodaDateTime)],
                          jobTags: Seq[(Int, String)],
                          jobsTags: Seq[(Int, Int)],
                          engineJobs: Seq[(Int, UUID, String, String, JodaDateTime, JodaDateTime, String, String, String, String, Option[String])],
                          jobResults: Seq[(Int, String)],
                          users: Seq[(Int, String, String, JodaDateTime, JodaDateTime)],
                          projects: Seq[(Int, String, String, String, JodaDateTime, JodaDateTime)],
                          projectsUsers: Seq[(Int, String, String)],
                          datasetTypes: Seq[(String, String, String, JodaDateTime, JodaDateTime, String)],
                          engineJobsDataSets: Seq[(Int, UUID, String)],
                          dsMetaData2: Seq[(Int, UUID, String, String, JodaDateTime, JodaDateTime, Long, Long, String, String, String, String, Int, Int, Int, Boolean)],
                          dsSubread2: Seq[(Int, UUID, String, String, String, String, String, Int, String, String, String, String)],
                          dsHdfSubread2: Seq[(Int, UUID, String, String, String, String, String, Int, String, String, String, String)],
                          dsReference2: Seq[(Int, UUID, String, String)],
                          dsGmapReference2: Seq[(Int, UUID, String, String)],
                          dsAlignment2: Seq[(Int, UUID)],
                          dsBarcode2: Seq[(Int, UUID)],
                          dsCCSread2: Seq[(Int, UUID)],
                          dsCCSAlignment2: Seq[(Int, UUID)],
                          dsContig2: Seq[(Int, UUID)],
                          datastoreServiceFiles: Seq[(UUID, String, String, Long, JodaDateTime, JodaDateTime, JodaDateTime, String, Int, UUID, String, String)],
                          runSummaries: Seq[(UUID, String, Option[String], Option[String], Option[JodaDateTime], Option[JodaDateTime], Option[JodaDateTime], String, Int, Int, Int, Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Boolean)],
                          dataModels: Seq[(String, UUID)],
                          collectionMetadata: Seq[(UUID, UUID, String, String, Option[String], Option[String], Option[String], String, Option[String], Option[String], Double, Option[JodaDateTime], Option[JodaDateTime], Option[String])],
                          samples: Seq[(String, UUID, String, String, JodaDateTime)])

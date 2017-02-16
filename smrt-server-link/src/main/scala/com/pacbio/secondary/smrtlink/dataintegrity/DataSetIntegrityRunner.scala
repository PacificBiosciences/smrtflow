package com.pacbio.secondary.smrtlink.dataintegrity

import java.nio.file.{Files, Paths}

import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.models.DataSetMetaDataSet

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


class DataSetIntegrityRunner(dao: JobsDao) extends BaseDataIntegrity {

  val runnerId = "smrtflow.dataintegrity.metatadataset"

  // This needs to do more, for real subclasses of DataSets, it should
  // check the external resources for
  private def isValid(ds: DataSetMetaDataSet): Boolean = Files.exists(Paths.get(ds.path))

  def run(): Future[MessageResponse] =  {
    for {
      results <- dao.getDataSetMetas(None)
      inValidIds <- Future { results.filter(isValid).map(_.id).toSet }
      resultsMessage <- dao.updatedDataSetMetasAsInActive(inValidIds)
    } yield resultsMessage
  }
}

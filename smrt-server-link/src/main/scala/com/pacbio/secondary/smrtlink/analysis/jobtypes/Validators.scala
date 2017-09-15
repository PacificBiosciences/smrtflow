package com.pacbio.secondary.smrtlink.analysis.jobtypes

import java.nio.file.{Paths, Files}

import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.InvalidJobOptionError

/**
  * General Validation utils
  * Created by mkocher on 9/26/15.
  */
object Validators {

  def fileExists(x: String): Option[InvalidJobOptionError] = {
    if (Files.exists(Paths.get(x))) None
    else Some(InvalidJobOptionError(s"Unable to find $x"))
  }

  def filesExists(xs: Seq[String]): Option[InvalidJobOptionError] = {
    val rx = xs.map(fileExists).flatMap(x => x)
    rx.reduceLeftOption((a, b) => InvalidJobOptionError(a.msg + " " + b.msg))
  }

  def validateDataSetType(datasetType: String): Option[InvalidJobOptionError] = {
    DataSetMetaTypes.toDataSetType(datasetType) match {
      case Some(x) => None
      case None =>
        val supportedTypes = DataSetMetaTypes.ALL
          .map(_.toString)
          .reduceLeft((acc, b) => acc + " " + b)
        Some(InvalidJobOptionError(
          s"Invalid DataSet type $datasetType. Supported Types $supportedTypes"))
    }
  }

}

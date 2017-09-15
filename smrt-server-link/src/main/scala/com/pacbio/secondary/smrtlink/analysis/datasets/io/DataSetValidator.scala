package com.pacbio.secondary.smrtlink.analysis.datasets.io

import java.net.URI
import java.nio.file.{Path, Paths, Files}

import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetIO,
  SubreadSetIO
}
import com.pacificbiosciences.pacbiobasedatamodel.IndexedDataType.FileIndices
import com.typesafe.scalalogging.LazyLogging

import collection.JavaConversions._

import com.pacificbiosciences.pacbiodatasets.{
  SubreadSet,
  DataSetType,
  ReadSetType
}
import com.pacificbiosciences.pacbiobasedatamodel.{
  ExternalResource,
  StrictEntityType,
  ExternalResources
}

import scala.collection.mutable

/**
  *
  * Created by mkocher on 5/17/15.
  */
object DataSetValidator extends LazyLogging {

  /**
    * Resolve the ResourceId path/uri to
    *
    * @param resource
    * @param rootDir
    * @return
    */
  private def resourceToAbsolutePath(resource: String, rootDir: Path): Path = {
    val uri = URI.create(resource)
    val path =
      if (uri.getScheme == null) Paths.get(resource) else Paths.get(uri)
    val realPath =
      if (path.isAbsolute) path.toAbsolutePath
      else rootDir.resolve(path).normalize().toAbsolutePath
    realPath
  }

  /**
    * Generic validation mechanism.
    *
    * Validate paths of external references are found
    * Validate total length and num records are consistent
    *
    * @param dataset A PacBio DataSet
    * @tparam T a subclass of DataSet type
    * @param rootDir Root path to the dataset file
    * @return
    */
  def validate[T <: DataSetType](dataset: T,
                                 rootDir: Path): Either[String, T] = {

    // ExternalResources property can be null
    if (dataset.getExternalResources == null) {
      logger.warn(
        s"Root level external resources is null. No external resources to validate for $dataset")
      Right(dataset)
    } else {
      val rs = dataset.getExternalResources.getExternalResource
      if (rs.nonEmpty) {
        //validateExternalResources(dataset.getExternalResources, rootDir)
        Right(dataset)
      } else {
        // This should probably raise. The root level should have at least on dataset,
        // even though the XSD allows it.
        logger.warn(
          s"Root level external references are empty. No external resources to validate for $dataset")
        Right(dataset)
      }
    }
  }

  private def entitySummary[T <: StrictEntityType](x: T) =
    s"External Resource ${x.getUniqueId} \n MetaType ${x.getMetaType} \n ResourceId ${x.getResourceId} \n Exists? ${Files
      .exists(Paths.get(x.getResourceId))}"

  private def summarizeFileIndices(fileIndices: FileIndices) = {
    if (fileIndices != null) {
      fileIndices.getFileIndex
        .map(i => entitySummary(i))
        .reduceOption(_ + "\n" + _)
        .getOrElse("")
    } else ""
  }

  private def summarizeExternalResources(
      externalResources: ExternalResources): String = {

    val outs = mutable.MutableList.empty[String]

    if (externalResources != null) {
      val ers = externalResources.getExternalResource
      outs += s" Number of external resources ${ers.length}"
      ers
        .filter(_ != null)
        .foreach { x =>
          val sx = entitySummary[ExternalResource](x)
          outs += sx
          outs += summarizeExternalResources(x.getExternalResources)
          outs += summarizeFileIndices(x.getFileIndices)
        }
    }
    outs.reduceOption(_ + "\n" + _).getOrElse("")
  }

  /**
    * General Summary info for the DataSet
    *
    * @param dataset DataSet instance
    * @tparam T
    * @return
    */
  def summarize[T <: DataSetType](dataset: T): String = {

    val outs =
      mutable.MutableList(
        s"DataSet summary $dataset",
        s" Meta type           ${dataset.getMetaType}",
        s" Id                  ${dataset.getUniqueId}",
        s" name                ${dataset.getName}",
        s" Created At          ${dataset.getCreatedAt}",
        s" Version             ${dataset.getVersion}",
        s" Description         ${dataset.getDescription}"
      )

    // Having a dataset without any root level external resources
    // makes no sense, but it's allow in the XSD.
    // Print a warning here.
    if (dataset.getExternalResources == null) {
      outs += "WARNING! Dataset has NO external Resources."
    } else {
      if (dataset.getExternalResources.getExternalResource.isEmpty) {
        outs += "WARNING! Dataset has empty external Resources."
      }
    }

    outs += summarizeExternalResources(dataset.getExternalResources)

    outs.reduce(_ + "\n" + _)
  }

}

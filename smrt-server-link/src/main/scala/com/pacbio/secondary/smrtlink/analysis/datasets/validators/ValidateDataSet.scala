package com.pacbio.secondary.smrtlink.analysis.datasets.validators

import java.nio.file.{Paths, Files, Path}
import java.net.URI
import java.util.UUID

import collection.JavaConverters._

import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes.FileType
import com.pacificbiosciences.pacbiodatasets.DataSetType

import cats.data._
import cats.data.Validated._
import cats.implicits._

/**
  * General utils for validating PacBio DataSets
  *
  * Created by mkocher on 11/18/15.
  */
trait ValidateDataSet {

  type DsType <: DataSetType

  // Supported file types in the External Resources MetaTypes
  val supportedFileTypes: Set[FileType]

  /**
    * Every DataSet should have at least one External Resource
    *
    * @param ds
    * @return
    */
  def hasAtLeastOneExternalResource(ds: DsType): ValidationResult[DsType] = {
    val msg =
      s"DataSet ${ds.getUniqueId} must have at least 1 external resource."
    if (ds.getExternalResources.getExternalResource.isEmpty) msg.invalidNel
    else ds.validNel
  }

  /**
    * Each dataset has a specific set of allowed metatypes that are allowed
    *
    * @param ds
    * @return
    */
  def hasExternalResourceMetaType(ds: DsType): ValidationResult[DsType] = {
    !ds.getExternalResources.getExternalResource.asScala.exists(er =>
      supportedFileTypes.map(_.fileTypeId) contains er.getMetaType) match {
      case true =>
        s"Failed to find required metatype $supportedFileTypes".invalidNel
      case false => ds.validNel
    }
  }

  private def getExternalResourcePath(resourceId: String): Path = {
    if (resourceId.startsWith("file://")) Paths.get(URI.create(resourceId))
    else Paths.get(resourceId)
  }

  /**
    * Validate the Resource Path of the fasta file is found
    *
    * @param ds
    * @return
    */
  def hasValidExternalResourcePaths(ds: DsType): ValidationResult[DsType] = {
    ds.getExternalResources.getExternalResource.asScala
      .map(r => r.getResourceId)
      .filter(x => !Files.exists(getExternalResourcePath(x)))
      .reduceLeftOption((a, b) => s"$a, $b") match {
      case Some(msg) => s"Unable to find Resource(s) $msg".invalidNel
      case _ => ds.validNel
    }
  }

  def hasValidId(ds: DsType): ValidationResult[DsType] = {
    try {
      UUID.fromString(ds.getUniqueId)
      ds.validNel
    } catch {
      case e: Exception => e.getMessage.invalidNel
    }
  }

  def validateCore(ds: DsType): ValidationResult[DsType] =
    (hasAtLeastOneExternalResource(ds),
     hasValidId(ds),
     hasValidExternalResourcePaths(ds))
      .mapN[DsType]((_: DsType, _: DsType, _: DsType) => ds)

  /**
    * DataSet specific sets
    *
    * @param ds
    * @return
    */
  def validateCustom(ds: DsType): ValidationResult[DsType] = {
    ds.validNel
  }

  def validator(ds: DsType): ValidationResult[DsType] =
    (validateCore(ds), validateCustom(ds))
      .mapN[DsType]((_: DsType, _: DsType) => ds)
}

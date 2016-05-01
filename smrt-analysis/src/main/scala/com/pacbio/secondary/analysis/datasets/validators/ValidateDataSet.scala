package com.pacbio.secondary.analysis.datasets.validators

import java.nio.file.{Paths, Files}
import java.util.UUID

import collection.JavaConversions._
import collection.JavaConverters._

import com.pacbio.secondary.analysis.constants.FileTypes.FileType
import com.pacificbiosciences.pacbiodatasets.DataSetType

import scalaz._
import Scalaz._


/**
  * General utils for validating PacBio DataSets
  *
  * Created by mkocher on 11/18/15.
  */
trait ValidateDataSet {

  type DsType <: DataSetType
  type ValidateDataSetE = ValidationNel[String, DsType]

  // Supported file types in the External Resources MetaTypes
  val supportedFileTypes: Set[FileType]

  /**
    * Every DataSet should have at least one External Resource
    * @param ds
    * @return
    */
  def hasAtLeastOneExternalResource(ds: DsType): ValidateDataSetE = {
    val msg = s"DataSet ${ds.getUniqueId} must have at least 1 external resource."
    if (ds.getExternalResources.getExternalResource.isEmpty) msg.failNel else ds.successNel
  }

  /**
    * Each dataset has a specific set of allowed metatypes that are allowed
    * @param ds
    * @return
    */
  def hasExternalResourceMetaType(ds: DsType): ValidateDataSetE = {
    !ds.getExternalResources.getExternalResource.
      exists(er => supportedFileTypes contains er.getMetaType) match {
      case true => s"Failed to find required metatype $supportedFileTypes".failNel
      case false => ds.successNel
    }
  }

  /**
    *   Validate the Resource Path of the fasta file is found
    * @param ds
    * @return
    */
  def hasValidExternalResourcePaths(ds: DsType): ValidateDataSetE = {
    ds.getExternalResources.getExternalResource.map(r => r.getResourceId)
      .filter(x => !Files.exists(Paths.get(x)))
      .reduceLeftOption((a, b) => s"$a, $b") match {
      case Some(msg) => s"Unable to find Resource(s) $msg".failNel
      case _ => ds.successNel
    }
  }

  def hasValidId(ds: DsType): ValidateDataSetE = {
    try {UUID.fromString(ds.getUniqueId)
        ds.successNel
    }  catch {
      case e: Exception => e.getMessage.failureNel
    }
  }

  def validateCore(ds: DsType) = (hasAtLeastOneExternalResource(ds) |@|
    hasValidId(ds) |@|
    hasValidExternalResourcePaths(ds))((_, _, _) => ds)

  /**
    * DataSet specific sets
    * @param ds
    * @return
    */
  def validateCustom(ds: DsType): ValidateDataSetE = {
    ds.successNel
  }

  def validator(ds: DsType): ValidateDataSetE = (validateCore(ds) |@| validateCustom(ds))((_, _) => ds)
}

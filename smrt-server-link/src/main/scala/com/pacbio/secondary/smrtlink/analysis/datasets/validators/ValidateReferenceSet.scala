package com.pacbio.secondary.smrtlink.analysis.datasets.validators

import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.LazyLogging

import collection.JavaConversions._
import collection.JavaConverters._

import scalaz._
import Scalaz._

import com.pacificbiosciences.pacbiodatasets.DataSetType
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes.{
  FileType,
  FileBaseType,
  IndexFileBaseType
}
import com.pacbio.secondary.smrtlink.analysis.datasets.InValidDataSetError
import com.pacificbiosciences.pacbiodatasets.{ReferenceSet, GmapReferenceSet}

/**
  * Misc utils for validating that the ReferenceSet is valid
  *
  * Created by mkocher on 9/29/15.
  */
trait ValidateReferenceSetBase[T <: DataSetType]
    extends ValidateDataSet
    with LazyLogging {

  type DsType = T

  val supportedFileTypes: Set[FileType] = Set(FileTypes.FASTA_REF)

  def apply(rs: T): Option[InValidDataSetError] = validate(rs)

  /**
    * Core Validation func
    *
    * @param rs
    * @return
    */
  def validate(rs: T): Option[InValidDataSetError] = {
    // keep backward compatibility interface
    validator(rs) match {
      case Success(_) => None
      case Failure(nel) => Some(InValidDataSetError(s"Failed ${nel}"))
    }
  }

  override def validateCustom(rs: T) = {
    (hasAtLeastOneExternalResource(rs) |@|
      hasRequiredIndexFiles(rs) |@|
      validateExternalResourcePaths(rs))((_, _, _) => rs)
  }

  def hasRequiredIndexFiles(rs: T): ValidateDataSetE

  // Validate the Resource Path of the fasta file is found
  def validateExternalResourcePaths(rs: T): ValidateDataSetE = {
    rs.getExternalResources.getExternalResource
      .map(r => r.getResourceId)
      .filter(x => !Files.exists(Paths.get(x)))
      .reduceLeftOption((a, b) => s"$a, $b") match {
      case Some(msg) => s"Unable to find Resource(s) $msg".failNel
      case _ => rs.successNel
    }
  }

}

object ValidateReferenceSet extends ValidateReferenceSetBase[ReferenceSet] {

  private def validateIndexMetaTypeExists(
      metaType: IndexFileBaseType,
      rs: ReferenceSet): ValidateDataSetE = {
    rs.getExternalResources.getExternalResource
      .map(x => x.getFileIndices)
      .flatMap(i => i.getFileIndex)
      .find(_.getMetaType == metaType.fileTypeId) match {
      case Some(fx) => rs.successNel
      case None =>
        s"Unable to find required External Resource MetaType ${metaType.fileTypeId}".failNel
    }
  }

  private def validateIndexMetaTypePathExists(
      metaType: IndexFileBaseType,
      rs: ReferenceSet): ValidateDataSetE = {
    rs.getExternalResources.getExternalResource
      .map(x => x.getFileIndices)
      .flatMap(i => i.getFileIndex)
      .find(_.getMetaType == metaType.fileTypeId)
      .filter(x => !Files.exists(Paths.get(x.getResourceId))) match {
      case Some(fx) =>
        s"Resource Path not found ${fx.getResourceId} for ${metaType.fileTypeId}".failNel
      case None => rs.successNel
    }
  }

  def validateIndexMetaType(metaType: IndexFileBaseType,
                            rs: ReferenceSet): ValidateDataSetE = {
    (validateIndexMetaTypeExists(metaType, rs) |@|
      validateIndexMetaTypePathExists(metaType, rs))((_, _) => rs)
  }

  def validateIndexFai =
    validateIndexMetaType(FileTypes.I_SAM, _: ReferenceSet)

  def validateIndexSawriter =
    validateIndexMetaType(FileTypes.I_SAW, _: ReferenceSet)

  def hasRequiredIndexFiles(rs: ReferenceSet): ValidateDataSetE = {
    (validateIndexFai(rs) |@|
      validateIndexSawriter(rs))((_, _) => rs)
  }
}

object ValidateGmapReferenceSet
    extends ValidateReferenceSetBase[GmapReferenceSet] {

  private def validateDbMetaTypeExists(
      metaType: FileBaseType,
      rs: GmapReferenceSet): ValidateDataSetE = {
    rs.getExternalResources.getExternalResource
      .map(x => x.getExternalResources)
      .flatMap(i => i.getExternalResource)
      .find(_.getMetaType == metaType.fileTypeId) match {
      case Some(fx) => rs.successNel
      case None =>
        s"Unable to find required External Resource MetaType ${metaType.fileTypeId}".failNel
    }
  }

  private def validateDbMetaTypePathExists(
      metaType: FileBaseType,
      rs: GmapReferenceSet): ValidateDataSetE = {
    rs.getExternalResources.getExternalResource
      .map(x => x.getExternalResources)
      .flatMap(i => i.getExternalResource)
      .find(_.getMetaType == metaType.fileTypeId)
      .filter(x => !Files.exists(Paths.get(x.getResourceId))) match {
      case Some(fx) =>
        s"Resource Path not found ${fx.getResourceId} for ${metaType.fileTypeId}".failNel
      case None => rs.successNel
    }
  }

  def validateDbJson(rs: GmapReferenceSet): ValidateDataSetE = {
    (validateDbMetaTypeExists(FileTypes.JSON, rs) |@|
      validateDbMetaTypePathExists(FileTypes.JSON, rs))((_, _) => rs)
  }

  def hasRequiredIndexFiles(rs: GmapReferenceSet): ValidateDataSetE = {
    validateDbJson(rs) //)((_) => rs)
  }
}

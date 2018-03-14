package com.pacbio.secondary.smrtlink.analysis.datasets.validators

import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.LazyLogging

import collection.JavaConverters._
import com.pacificbiosciences.pacbiodatasets.DataSetType
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes.{
  FileBaseType,
  FileType,
  IndexFileBaseType
}
import com.pacbio.secondary.smrtlink.analysis.datasets.InValidDataSetError
import com.pacificbiosciences.pacbiodatasets.{GmapReferenceSet, ReferenceSet}

import cats.data._
import cats.data.Validated._
import cats.implicits._

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
    validator(rs).toEither match {
      case Right(_) => None
      case Left(nel) =>
        Some(InValidDataSetError(s"Failed $nel"))
    }
  }

  override def validateCustom(rs: T): ValidationResult[T] = {
    (hasAtLeastOneExternalResource(rs),
     hasRequiredIndexFiles(rs),
     validateExternalResourcePaths(rs)).mapN((_: T, _: T, _: T) => rs)
  }

  def hasRequiredIndexFiles(rs: T): ValidationResult[DsType]

  // Validate the Resource Path of the fasta file is found
  def validateExternalResourcePaths(rs: T): ValidationResult[DsType] = {
    rs.getExternalResources.getExternalResource.asScala
      .map(r => r.getResourceId)
      .filter(x => !Files.exists(Paths.get(x)))
      .reduceLeftOption((a, b) => s"$a, $b") match {
      case Some(msg) => s"Unable to find Resource(s) $msg".invalidNel
      case _ => rs.validNel
    }
  }

}

object ValidateReferenceSet extends ValidateReferenceSetBase[ReferenceSet] {

  private def validateIndexMetaTypeExists(
      metaType: IndexFileBaseType,
      rs: ReferenceSet): ValidationResult[DsType] = {
    rs.getExternalResources.getExternalResource.asScala
      .map(x => x.getFileIndices)
      .flatMap(i => i.getFileIndex.asScala)
      .find(_.getMetaType == metaType.fileTypeId) match {
      case Some(fx) => rs.validNel
      case None =>
        s"Unable to find required External Resource MetaType ${metaType.fileTypeId}".invalidNel
    }
  }

  private def validateIndexMetaTypePathExists(
      metaType: IndexFileBaseType,
      rs: ReferenceSet): ValidationResult[DsType] = {
    rs.getExternalResources.getExternalResource.asScala
      .map(x => x.getFileIndices)
      .flatMap(i => i.getFileIndex.asScala)
      .find(_.getMetaType == metaType.fileTypeId)
      .filter(x => !Files.exists(Paths.get(x.getResourceId))) match {
      case Some(fx) =>
        s"Resource Path not found ${fx.getResourceId} for ${metaType.fileTypeId}".invalidNel
      case None => rs.validNel
    }
  }

  def validateIndexMetaType(metaType: IndexFileBaseType,
                            rs: ReferenceSet): ValidationResult[DsType] = {
    (validateIndexMetaTypeExists(metaType, rs),
     validateIndexMetaTypePathExists(metaType, rs))
      .mapN((_: DsType, _: DsType) => rs)
  }

  def validateIndexFai =
    validateIndexMetaType(FileTypes.I_SAM, _: ReferenceSet)

  def validateIndexSawriter =
    validateIndexMetaType(FileTypes.I_SAW, _: ReferenceSet)

  def hasRequiredIndexFiles(rs: ReferenceSet): ValidationResult[DsType] = {
    (validateIndexFai(rs), validateIndexSawriter(rs))
      .mapN((_: DsType, _: DsType) => rs)
  }
}

object ValidateGmapReferenceSet
    extends ValidateReferenceSetBase[GmapReferenceSet] {

  private def validateDbMetaTypeExists(
      metaType: FileBaseType,
      rs: GmapReferenceSet): ValidationResult[DsType] = {
    rs.getExternalResources.getExternalResource.asScala
      .map(x => x.getExternalResources)
      .flatMap(i => i.getExternalResource.asScala)
      .find(_.getMetaType == metaType.fileTypeId) match {
      case Some(fx) => rs.validNel
      case None =>
        s"Unable to find required External Resource MetaType ${metaType.fileTypeId}".invalidNel
    }
  }

  private def validateDbMetaTypePathExists(
      metaType: FileBaseType,
      rs: GmapReferenceSet): ValidationResult[DsType] = {
    rs.getExternalResources.getExternalResource.asScala
      .map(x => x.getExternalResources)
      .flatMap(i => i.getExternalResource.asScala)
      .find(_.getMetaType == metaType.fileTypeId)
      .filter(x => !Files.exists(Paths.get(x.getResourceId))) match {
      case Some(fx) =>
        s"Resource Path not found ${fx.getResourceId} for ${metaType.fileTypeId}".invalidNel
      case None => rs.validNel
    }
  }

  def validateDbJson(rs: GmapReferenceSet): ValidationResult[DsType] = {
    (validateDbMetaTypeExists(FileTypes.JSON, rs),
     validateDbMetaTypePathExists(FileTypes.JSON, rs))
      .mapN((_: DsType, _: DsType) => rs)
  }

  def hasRequiredIndexFiles(rs: GmapReferenceSet): ValidationResult[DsType] = {
    validateDbJson(rs) //)((_) => rs)
  }
}

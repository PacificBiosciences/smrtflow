package com.pacbio.secondary.analysis.datasets.validators

import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.LazyLogging

import collection.JavaConversions._
import collection.JavaConverters._

import scalaz._
import Scalaz._


import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.constants.FileTypes.{FileType, IndexFileBaseType}
import com.pacbio.secondary.analysis.datasets.InValidDataSetError
import com.pacificbiosciences.pacbiodatasets.ReferenceSet

/**
  * Misc utils for validating that the ReferenceSet is valid
  *
  * Created by mkocher on 9/29/15.
  */
object ValidateReferenceSet extends ValidateDataSet with LazyLogging {

  type DsType = ReferenceSet

  val supportedFileTypes: Set[FileType] = Set(FileTypes.FASTA_REF)

  def apply(rs: ReferenceSet): Option[InValidDataSetError] = validate(rs)

  /**
    * Core Validation func
    * @param rs
    * @return
    */
  def validate(rs: ReferenceSet): Option[InValidDataSetError] = {
    // keep backward compatibility interface
    validator(rs) match {
      case Success(_) => None
      case Failure(nel) => Some(InValidDataSetError(s"Failed ${nel}"))
    }
  }

  override def validateCustom(rs: ReferenceSet) = {
    (hasAtLeastOneExternalResource(rs) |@|
      hasRequiredIndexFiles(rs) |@|
      validateExternalResourcePaths(rs)) ((_, _, _) => rs)
  }


  private def validateIndexMetaTypeExists(metaType: IndexFileBaseType, rs: ReferenceSet): ValidateDataSetE = {
    rs.getExternalResources.getExternalResource.map(x => x.getFileIndices)
      .flatMap(i => i.getFileIndex)
      .find(_.getMetaType == metaType.fileTypeId) match {
      case Some(fx) => rs.successNel
      case None => s"Unable to find required External Resource MetaType ${metaType.fileTypeId}".failNel
    }
  }

  private def validateIndexMetaTypePathExists(metaType: IndexFileBaseType, rs: ReferenceSet): ValidateDataSetE = {
    rs.getExternalResources.getExternalResource.map(x => x.getFileIndices)
      .flatMap(i => i.getFileIndex)
      .find(_.getMetaType == metaType.fileTypeId)
      .filter(x => !Files.exists(Paths.get(x.getResourceId))) match {
      case Some(fx) => s"Resource Path not found ${fx.getResourceId} for ${metaType.fileTypeId}".failNel
      case None => rs.successNel
    }
  }

  def validateIndexMetaType(metaType: IndexFileBaseType, rs: ReferenceSet): ValidateDataSetE = {
    (validateIndexMetaTypeExists(metaType, rs) |@|
      validateIndexMetaTypePathExists(metaType, rs)) ((_, _) => rs)
  }

  def validateIndexFai = validateIndexMetaType(FileTypes.I_SAM, _: ReferenceSet)

  def validateIndexIndexer = validateIndexMetaType(FileTypes.I_INDEX, _: ReferenceSet)

  def validateIndexFastaContig = validateIndexMetaType(FileTypes.I_FCI, _: ReferenceSet)

  def validateIndexSawriter = validateIndexMetaType(FileTypes.I_SAW, _: ReferenceSet)

  def hasRequiredIndexFiles(rs: ReferenceSet): ValidateDataSetE = {
    (validateIndexFai(rs) |@|
      validateIndexIndexer(rs) |@|
      validateIndexFastaContig(rs) |@|
      validateIndexSawriter(rs))((_, _, _, _) => rs)
  }

  // Validate the Resource Path of the fasta file is found
  def validateExternalResourcePaths(rs: ReferenceSet): ValidateDataSetE = {
    rs.getExternalResources.getExternalResource
      .map(r => r.getResourceId)
      .filter(x => !Files.exists(Paths.get(x)))
      .reduceLeftOption((a, b) => s"$a, $b") match {
      case Some(msg) => s"Unable to find Resource(s) $msg".failNel
      case _ => rs.successNel
    }
  }

}

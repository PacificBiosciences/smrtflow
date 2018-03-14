package com.pacbio.secondary.smrtlink.analysis.datasets.validators

import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes.FileType
import com.pacbio.secondary.smrtlink.analysis.datasets.validators.ValidateAlignmentSet._
import com.pacificbiosciences.pacbiodatasets.ConsensusReadSet

import cats.data._
import cats.data.Validated._
import cats.implicits._

/**
  * Created by mkocher on 12/1/15.
  */
object ValidateConsensusReadSet extends ValidateDataSet {

  type DsType = ConsensusReadSet

  val supportedFileTypes: Set[FileType] = Set(FileTypes.BAM_CCS)

  /**
    * Custom SubreadSet Validation
    *
    * @param ds
    * @return
    */
  override def validateCustom(ds: ConsensusReadSet): ValidationResult[DsType] = {
    ds.validNel
  }
}

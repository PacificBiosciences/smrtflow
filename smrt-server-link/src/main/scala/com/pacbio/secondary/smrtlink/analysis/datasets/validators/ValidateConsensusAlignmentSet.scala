package com.pacbio.secondary.smrtlink.analysis.datasets.validators

import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes.FileType
import com.pacificbiosciences.pacbiodatasets.ConsensusAlignmentSet

import scalaz._
import Scalaz._

/**
 * Created by mkocher on 12/1/15.
 */
object ValidateConsensusAlignmentSet extends ValidateDataSet{

  type DsType = ConsensusAlignmentSet

  val supportedFileTypes:Set[FileType] = Set(FileTypes.BAM_CCS_ALN)

  /**
   * Custom SubreadSet Validation
   *
   * @param ds
   * @return
   */
  override def validateCustom(ds: ConsensusAlignmentSet): ValidateDataSetE = {
    ds.successNel
  }
}

package com.pacbio.secondary.analysis.datasets.validators

import scalaz._
import Scalaz._

import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.constants.FileTypes.FileType
import com.pacbio.secondary.analysis.datasets.validators.ValidateAlignmentSet._
import com.pacificbiosciences.pacbiodatasets.ConsensusReadSet

/**
  * Created by mkocher on 12/1/15.
  */
object ValidateConsensusReadSet extends ValidateDataSet{

  type DsType = ConsensusReadSet

  val supportedFileTypes:Set[FileType] = Set(FileTypes.BAM_CCS)

  /**
    * Custom SubreadSet Validation
    *
    * @param ds
    * @return
    */
  override def validateCustom(ds: ConsensusReadSet): ValidateDataSetE = {
    ds.successNel
  }

}

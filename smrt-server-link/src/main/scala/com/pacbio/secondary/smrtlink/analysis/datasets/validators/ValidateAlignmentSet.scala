package com.pacbio.secondary.smrtlink.analysis.datasets.validators

import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes.FileType
import com.pacificbiosciences.pacbiodatasets.AlignmentSet

import scalaz._
import Scalaz._

/**
 * Created by mkocher on 12/1/15.
 */
object ValidateAlignmentSet extends ValidateDataSet{

  type DsType = AlignmentSet

  val supportedFileTypes:Set[FileType] = Set(FileTypes.BAM_ALN)

  /**
   * Custom SubreadSet Validation
   *
   * @param ds
   * @return
   */
  override def validateCustom(ds: AlignmentSet): ValidateDataSetE = {
    ds.successNel
  }

}

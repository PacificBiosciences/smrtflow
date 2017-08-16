package com.pacbio.secondary.smrtlink.analysis.datasets.validators

import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes.FileType

import scalaz._
import Scalaz._


import com.pacificbiosciences.pacbiodatasets.SubreadSet

/**
 * Created by mkocher on 11/18/15.
 */
object ValidateSubreadSet extends ValidateDataSet{

  type DsType = SubreadSet

  // FIXME. There's a longer list of supported file types (e.g., scraps, hqregion)
  val supportedFileTypes:Set[FileType] = Set(FileTypes.BAM_SUB)

  /**
   * Custom SubreadSet Validation
   *
   * @param ds
   * @return
   */
  override def validateCustom(ds: SubreadSet): ValidateDataSetE = {
    ds.successNel
  }

}

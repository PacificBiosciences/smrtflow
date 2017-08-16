package com.pacbio.secondary.smrtlink.analysis.datasets.validators

import scalaz._
import Scalaz._

import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes.FileType
import com.pacificbiosciences.pacbiodatasets.HdfSubreadSet

/**
 * Created by mkocher on 11/30/15.
 */
object ValidateHdfSubreadSet extends ValidateDataSet{

  type DsType = HdfSubreadSet

  val supportedFileTypes:Set[FileType] = Set(FileTypes.BAX)

  /**
   * Custom SubreadSet Validation
   *
   * @param ds
   * @return
   */
  override def validateCustom(ds: HdfSubreadSet): ValidateDataSetE = {
    ds.successNel
  }

}

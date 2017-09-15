package com.pacbio.secondary.smrtlink.analysis.datasets.validators

import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes.FileType
import com.pacificbiosciences.pacbiodatasets.BarcodeSet

import scalaz._
import Scalaz._

/**
  * Created by mkocher on 12/1/15.
  */
object ValidateBarcodeSet extends ValidateDataSet {

  type DsType = BarcodeSet

  val supportedFileTypes: Set[FileType] = Set(FileTypes.FASTA_BC)

  /**
    * Custom SubreadSet Validation
    *
    * @param ds
    * @return
    */
  override def validateCustom(ds: BarcodeSet): ValidateDataSetE = {
    ds.successNel
  }
}

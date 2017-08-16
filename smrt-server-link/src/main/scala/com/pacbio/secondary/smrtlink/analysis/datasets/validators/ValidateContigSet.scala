package com.pacbio.secondary.smrtlink.analysis.datasets.validators


import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes.FileType
import com.pacificbiosciences.pacbiodatasets.ContigSet

import scalaz._
import Scalaz._

/**
 * Created by mkocher on 12/1/15.
 */
object ValidateContigSet extends ValidateDataSet{

  type DsType = ContigSet

  val supportedFileTypes:Set[FileType] = Set(FileTypes.FASTA_CONTIG)

  /**
   * Custom SubreadSet Validation
   *
   * @param ds
   * @return
   */
  override def validateCustom(ds: ContigSet): ValidateDataSetE = {
    ds.successNel
  }
}

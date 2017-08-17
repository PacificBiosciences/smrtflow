package com.pacbio.secondary.smrtlink.analysis

import org.eclipse.persistence.exceptions.ConversionException

package object converters {

  // Base Exception
  trait ConversionException {
    val msg: String
  }

  trait FastaConversionError extends ConversionException

  case class InValidFastaRecordError(msg: String) extends Exception(msg) with FastaConversionError

  case class InValidFastaFileError(msg: String) extends Exception(msg) with FastaConversionError

  case class IndexCreationError(msg: String) extends Exception(msg) with FastaConversionError

  case class DatasetConvertError(msg: String) extends Exception(msg) with ConversionException

  case class ConvertSuccess(message: String)

}

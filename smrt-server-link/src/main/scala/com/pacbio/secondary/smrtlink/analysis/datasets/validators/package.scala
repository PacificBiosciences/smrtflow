package com.pacbio.secondary.smrtlink.analysis.datasets

import java.nio.file.Path

import com.pacificbiosciences.pacbiodatasets.{DataSetType => XmlDataSetType, _}

import scalaz._

/**
  * Created by mkocher on 4/11/16.
  */
package object validators {

  object ImplicitDataSetValidators {

    abstract class DataSetValidator[T <: XmlDataSetType] {
      def validate(ds: T): ValidationNel[String, T]
    }

    implicit object SubreadSetValidator extends DataSetValidator[SubreadSet] {
      def validate(ds: SubreadSet) = ValidateSubreadSet.validator(ds)
    }

    implicit object HdfSubreadSetValidator
        extends DataSetValidator[HdfSubreadSet] {
      def validate(ds: HdfSubreadSet) = ValidateHdfSubreadSet.validator(ds)
    }

    implicit object AlignmentSetValidator
        extends DataSetValidator[AlignmentSet] {
      def validate(ds: AlignmentSet) = ValidateAlignmentSet.validator(ds)
    }

    implicit object ConsensusAlignmentSetValidator
        extends DataSetValidator[ConsensusAlignmentSet] {
      def validate(ds: ConsensusAlignmentSet) =
        ValidateConsensusAlignmentSet.validator(ds)
    }

    implicit object BarcodeSetValidator extends DataSetValidator[BarcodeSet] {
      def validate(ds: BarcodeSet) = ValidateBarcodeSet.validator(ds)
    }

    implicit object ContigSetValidator extends DataSetValidator[ContigSet] {
      def validate(ds: ContigSet) = ValidateContigSet.validator(ds)
    }

    implicit object ConsensusReadSetValidator
        extends DataSetValidator[ConsensusReadSet] {
      def validate(ds: ConsensusReadSet) =
        ValidateConsensusReadSet.validator(ds)
    }

    implicit object ReferenceSetValidator
        extends DataSetValidator[ReferenceSet] {
      def validate(ds: ReferenceSet) = ValidateReferenceSet.validator(ds)
    }

    implicit object GmapReferenceSetValidator
        extends DataSetValidator[GmapReferenceSet] {
      def validate(ds: GmapReferenceSet) =
        ValidateGmapReferenceSet.validator(ds)
    }

    def validator[T <: XmlDataSetType](ds: T)(
        implicit vx: DataSetValidator[T]) = {
      vx.validate(ds: T)
    }
  }

}

package com.pacbio.secondary.smrtlink.client

/**
  * Created by mkocher on 3/1/17.
  */
trait AnalysisJobConstants extends JobTypesConstants {
  val IMPORT_DSTORE = "import-datastore"
  val CONVERT_FASTA = "convert-fasta-reference"
  val CONVERT_BARCODES = "convert-fasta-barcodes"
  val CONVERT_MOVIE = "convert-rs-movie"
  val EXPORT_DS = "export-datasets"
  val DELETE_DS = "delete-datasets"
  val PB_PIPE = "pbsmrtpipe"
}

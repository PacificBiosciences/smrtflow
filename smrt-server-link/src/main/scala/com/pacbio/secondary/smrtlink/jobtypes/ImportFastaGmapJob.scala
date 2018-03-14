package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Path, Paths}

import scala.util.Try

import com.pacificbiosciences.pacbiodatasets.{
  ContigSetMetadataType,
  GmapReferenceSet
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.converters.GmapReferenceConverter
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetMetaTypes,
  GmapReferenceSetIO
}

case class ImportFastaGmapJobOptions(path: String,
                                     ploidy: String,
                                     organism: String,
                                     name: Option[String],
                                     description: Option[String],
                                     projectId: Option[Int] = Some(
                                       JobConstants.GENERAL_PROJECT_ID))
    extends ImportFastaBaseJobOptions {
  override def jobTypeId = JobTypeIds.CONVERT_FASTA_GMAPREFERENCE

  override def toJob() = new ImportFastaGmapJob(this)
}

class ImportFastaGmapJob(opts: ImportFastaGmapJobOptions)
    extends ImportFastaBaseJob[GmapReferenceSet,
                               ContigSetMetadataType,
                               GmapReferenceSetIO](opts) {
  override val PIPELINE_ID =
    "pbsmrtpipe.pipelines.sa3_fasta_to_gmap_reference"
  override val DS_METATYPE = DataSetMetaTypes.GmapReference
  override val CONVERTER = GmapReferenceConverter
}

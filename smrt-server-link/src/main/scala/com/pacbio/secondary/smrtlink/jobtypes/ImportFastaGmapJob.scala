package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Path, Paths}

import scala.util.Try

import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.converters.GmapReferenceConverter
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetMetaTypes,
  DataSetIO
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
    extends ImportFastaBaseJob(opts) {
  override val PIPELINE_ID =
    "pbsmrtpipe.pipelines.sa3_ds_fasta_to_gmapreference"
  override val DS_METATYPE = DataSetMetaTypes.GmapReference

  override def runConverter(opts: ImportFastaBaseJobOptions,
                            outputDir: Path): Try[DataSetIO] =
    GmapReferenceConverter
      .toTry(opts.name.getOrElse(DEFAULT_REFERENCE_SET_NAME),
             Option(opts.organism),
             Option(opts.ploidy),
             Paths.get(opts.path),
             outputDir)
}

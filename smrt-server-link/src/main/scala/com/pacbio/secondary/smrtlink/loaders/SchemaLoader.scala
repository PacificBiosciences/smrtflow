package com.pacbio.secondary.smrtlink.loaders

import com.pacbio.secondary.smrtlink.models._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.IOUtils

import scala.collection.mutable

/**
 * Temp version to get schemas in the end points.
 *
 * This should be replaced by:
 *
 * https://github.com/fge/json-schema-validator
 *
 * Created by mkocher on 5/14/15.
 */
trait SchemaLoader extends LazyLogging{

  val ROOT_SCHEMAS = "/schemas/datasets/"

  private val schemas = mutable.Map[String, PacBioSchema]()

  def registerSchema(pacBioSchema: PacBioSchema): PacBioSchema = {
    schemas(pacBioSchema.id) = pacBioSchema
    pacBioSchema
  }

  /**
   * Load a Resource from a stream
   *
   * @param id
   * @param loadPath
   * @return
   */
  def loaderSchema(id: String, loadPath: String): PacBioSchema = {
    val p = ROOT_SCHEMAS + loadPath
    logger.info(s"Loading schema $id from '$p'")
    val inputStream = getClass.getResourceAsStream(ROOT_SCHEMAS + loadPath)
    val encoding = "utf8"
    val content = IOUtils.toString(inputStream, encoding)
    PacBioSchema(id, content)
  }

  def loadAndRegister(id: String, loadPath: String) = {
    registerSchema(loaderSchema(id, loadPath))
  }

  val alignmentSchema = loadAndRegister("pacbio.secondary.schemas.datasets.alignments", "alignment.schema.json")
  val referenceSchema = loadAndRegister("pacbio.secondary.schemas.datasets.references", "reference.schema.json")
  val subreadSchema = loadAndRegister("pacbio.secondary.schemas.datasets.subreads", "subread.schema.json")
  val barcodeSchema = loadAndRegister("pacbio.secondary.schemas.datasets.barcodes", "barcode.schema.json")
  val ccsAlignmentSchema = loadAndRegister("pacbio.secondary.schemas.datasets.alignments", "ccs-alignment.schema.json")
  val ccsReadSchema = loadAndRegister("pacbio.secondary.schemas.datasets.ccsreads", "ccs-read.schema.json")
  val contigSchema = loadAndRegister("pacbio.secondary.schemas.datasets.contig", "contigs.schema.json")
  val gmapReferenceSchema = loadAndRegister("pacbio.secondary.schemas.datasets.gmapreferences", "gmapreference.schema.json")
}

object SchemaLoader extends SchemaLoader

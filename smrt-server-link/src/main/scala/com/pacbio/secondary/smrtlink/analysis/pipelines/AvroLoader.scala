package com.pacbio.secondary.smrtlink.analysis.pipelines

import java.nio.file.{Paths, Path}
import collection.JavaConversions._
import collection.JavaConverters._

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.file.DataFileReader
import org.apache.avro.specific.SpecificDatumReader
import org.apache.commons.io.FileUtils

/**
 *
 * Created by mkocher on 9/19/15.
 */
trait AvroLoader[T] extends Loader[T] with LazyLogging{

  val extFilter = Seq("avro")

  /**
   * Load an Avro File
   * @param path Path Avro file
   * @return
   */
  def loadFrom(path: Path): T = {
    logger.debug(s"Loading file from $path")

    val avroReader = new SpecificDatumReader[T]
    val avroFileReader = new DataFileReader[T](path.toFile, avroReader)

    val pipelineTemplate = avroFileReader.next()
    avroFileReader.close()
    pipelineTemplate
  }
}

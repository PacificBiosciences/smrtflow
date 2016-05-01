package com.pacbio.secondary.analysis.contracts

import java.nio.file.Path

import com.pacbio.common.models.contracts.{ResolvedToolContract, ToolContract}
import org.apache.avro.file.DataFileReader
import org.apache.avro.specific.SpecificDatumReader

/**
 *
 * Created by mkocher on 8/19/15.
 */
object ContractLoaders {

  /**
   * Load a Resolved Tool Contract from an Avro file
   * @param path Path to avro file
   * @return
   */
  def loadResolvedToolContract(path: Path): ResolvedToolContract = {
    val reader = new SpecificDatumReader[ResolvedToolContract]
    val fileReader = new DataFileReader[ResolvedToolContract](path.toFile, reader)

    val rtc = fileReader.next()
    fileReader.close()
    rtc
  }

  /**
   * Load A tool Contract from an Avro file
   * @param path Path to avro file
   * @return
   */
  def loadToolContract(path: Path): ToolContract = {
    val reader = new SpecificDatumReader[ToolContract]
    val fileReader = new DataFileReader[ToolContract](path.toFile, reader)

    val tc = fileReader.next()
    fileReader.close()
    tc
  }
}

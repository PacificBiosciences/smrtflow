package com.pacbio.secondary.analysis.contracts

import java.nio.file.Path

import com.pacbio.common.models.contracts._

import collection.JavaConversions._
import collection.JavaConverters._

/**
 * Converting utils for Resolving a ToolContract
 * Created by mkocher on 8/10/15.
 *
 * Crude and Simple TC resolver
 */
object Resolver {

  def resolveToolContract(
      toolContract: ToolContract,
      inputFiles: Seq[String],
      maxProc: Int,
      outputDir: Path): ResolvedToolContract = {

    val tc = toolContract.getToolContract
    // Resolved Values
    val outputFiles = tc.getOutputTypes.asScala.map(x => outputDir.resolve(x.getDefaultName.toString))
    // Need to update the Schema
    val resolvedNproc = tc.getNproc

    val isDistributed = toolContract.getToolContract.getIsDistributed

    val driver = Driver.newBuilder().setExe(toolContract.getDriver.getExe).build()

    val task = ResolvedToolContractTask.newBuilder()
      .setInputFiles(inputFiles)
      .setNproc(resolvedNproc)
      .setIsDistributed(isDistributed)
      .setToolContractId(toolContract.getToolContract.getToolContractId)
      .setTaskType(toolContract.getToolContract.getTaskType)
      .setResources(List[String]())
      .build()
    ResolvedToolContract.newBuilder().setDriver(driver).setResolvedToolContract(task).build()

  }
}

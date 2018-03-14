package com.pacbio.secondary.smrtlink.analysis.contracts

import java.nio.file.Path
import java.util

import com.pacbio.common.models.contracts._

import collection.JavaConverters._

/**
  * Converting utils for Resolving a ToolContract
  * Created by mkocher on 8/10/15.
  *
  * Crude and Simple TC resolver
  */
object Resolver {

  def resolveToolContract(toolContract: ToolContract,
                          inputFiles: Seq[String],
                          maxProc: Int,
                          outputDir: Path): ResolvedToolContract = {

    val tc = toolContract.getToolContract
    // Resolved Values
    val outputFiles = tc.getOutputTypes.asScala.map(x =>
      outputDir.resolve(x.getDefaultName.toString))
    // Need to update the Schema
    val resolvedNproc = tc.getNproc

    val isDistributed = toolContract.getToolContract.getIsDistributed

    val driver =
      Driver.newBuilder().setExe(toolContract.getDriver.getExe).build()

    val javaFiles: java.util.List[CharSequence] =
      new util.ArrayList[CharSequence](inputFiles.asJavaCollection)
    val resources: java.util.List[CharSequence] =
      new util.ArrayList[CharSequence]()

    val task = ResolvedToolContractTask
      .newBuilder()
      .setInputFiles(javaFiles)
      .setNproc(resolvedNproc)
      .setIsDistributed(isDistributed)
      .setToolContractId(toolContract.getToolContract.getToolContractId)
      .setTaskType(toolContract.getToolContract.getTaskType)
      .setResources(resources)
      .build()
    ResolvedToolContract
      .newBuilder()
      .setDriver(driver)
      .setResolvedToolContract(task)
      .build()

  }
}

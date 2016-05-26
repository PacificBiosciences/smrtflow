package com.pacbio.secondary.lims.services

import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.LazyLogging
import com.pacbio.common.app.BaseApi
import com.pacbio.secondary.smrtlink.auth.SmrtLinkRolesInit


// TODO jfalkner: check/cleanup needed roles -- why is SmrtLinkRolesInit needed here?
trait LimsApi extends BaseApi with SmrtLinkRolesInit with LazyLogging {
  override val providers = new LimsProviders {}

  override def startup(): Unit = {
    try {
      providers.jobsDao().initializeDb()
    } catch {
      case e: Exception => {
        e.printStackTrace()
        system.shutdown()
      }
    }

    val p = Paths.get(providers.engineConfig.pbRootJobDir)
    if (!Files.exists(p)) {
      logger.info(s"Creating root job dir $p")
      Files.createDirectories(p)
    }
  }

  sys.addShutdownHook(system.shutdown())
}
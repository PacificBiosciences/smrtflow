package com.pacbio.secondary.smrtlink.utils

import java.util.UUID

import com.typesafe.config.Config

import scala.util.Try
import scala.util.control.NonFatal

/**
  * Created by mkocher on 4/22/17.
  */
trait SmrtServerIdUtils {


  private def computeUUID(): UUID = {
    val host = java.net.InetAddress.getLocalHost().getHostName()
    UUID.nameUUIDFromBytes(host.map(_.toByte).toArray)
  }

  def getSystemUUID(conf: Config): UUID = {

    val tx = Try {UUID.fromString(conf.getString("pacBioSystem.smrtLinkSystemId"))}

    tx.recoverWith {case NonFatal(_) => Try {computeUUID()}}.getOrElse(UUID.randomUUID())

  }
}

//trait SmrtServerIdProvider {
//  this:
//}

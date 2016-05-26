package com.pacbio.secondary.lims.services

import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.smrtlink.app.SmrtLinkProviders

// Setup all of the URL handling providers as one succinct trait to use later
trait LimsProviders extends SmrtLinkProviders with HelloWorldProvider {

  override val baseServiceId: Singleton[String] = Singleton("smrtlink_lims")
  override val actorSystemName = Some("smrtlink-lims")
  override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)

  // TODO jfalkner: all for port to be specified
  override val serverPort: Singleton[Int] = Singleton(8081)
}

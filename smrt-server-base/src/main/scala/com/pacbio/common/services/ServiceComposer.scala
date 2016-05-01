package com.pacbio.common.services

import scala.collection.mutable.ArrayBuffer

import spray.routing.{RouteConcatenation, Route}

import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.dependency.Singleton

trait ServiceComposer extends RouteConcatenation with RouteProvider {
  val services = ArrayBuffer.empty[Singleton[PacBioService]]

  def addService(service: Singleton[PacBioService]) = {
    services += service
  }

  def routes(): Route = {
    services.map(_().prefixedRoutes).reduce(_ ~ _)
  }

  def manifests(): Seq[PacBioComponentManifest] = {
    services.map(_().manifest)
  }
}

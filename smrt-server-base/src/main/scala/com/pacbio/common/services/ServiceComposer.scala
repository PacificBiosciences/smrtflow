package com.pacbio.common.services

import scala.collection.mutable.ArrayBuffer
import spray.routing.{Route, RouteConcatenation}
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.dependency.Singleton

import scala.collection.mutable

trait ServiceComposer extends RouteConcatenation with RouteProvider {
  val services = ArrayBuffer.empty[Singleton[PacBioService]]
  private val _manifests = mutable.Set.empty[PacBioComponentManifest]

  // Enable loading manifests that are "external" to the system (e.g., SL, SL UI)
  def addManifest(m: PacBioComponentManifest): PacBioComponentManifest = {
    _manifests.add(m)
    m
  }

  def addManifests(ms: Set[PacBioComponentManifest]): Set[PacBioComponentManifest] = {
    _manifests ++= ms
    ms
  }

  def addService(service: Singleton[PacBioService]) = {
    services += service
  }

  def routes(): Route = {
    services.map(_().prefixedRoutes).reduce(_ ~ _)
  }

  // This is a clumsy way to create a Set with 'id' being the
  // single component to compute equality
  def manifests(): Set[PacBioComponentManifest] = {
    (services.map(_().manifest).toSet ++ _manifests)
      .toList
      .map(x => (x.id, x))
      .toMap.values.toSet
  }

}

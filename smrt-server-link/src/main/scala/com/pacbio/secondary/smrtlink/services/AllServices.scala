package com.pacbio.secondary.smrtlink.services

import akka.http.scaladsl.server.{Route, RouteConcatenation}
import com.pacbio.secondary.smrtlink.dependency._
import com.pacbio.secondary.smrtlink.models.PacBioComponentManifest

/**
  * {{{SetBinding}}} for spray services that must be included in the total set of services.
  */
object AllServices extends SetBinding[PacBioService]

/**
  * This {{{SetBinding}}} is for services that need to be included in the set of all services, but don't provide a
  * manifest. (In particular, this is required for the {{{ManifestService}}}, to avoid circular dependency.)
  */
object NoManifestServices extends SetBinding[PacBioService]

trait RouteProvider {
  def routes(): Route
}

/**
  * Provides the merged routes of all services. Services that need to be included must be bound to the
  * {{{AllServices}}} binding in their providers. Concrete providers must mixin {{{SetBindings}}}.
  */
trait ServiceRoutesProvider extends RouteConcatenation with RouteProvider {
  this: SetBindings =>

  def routes(): Route =
    (set(AllServices) ++ set(NoManifestServices))
      .map(_.prefixedRoutes)
      .reduce(_ ~ _)
}

/**
  * Provides the set of manifests of all services as a Singleton. Services that need to be included must be bound to the
  * {{{AllServices}}} binding in their providers. Concrete providers must mixin {{{SetBindings}}}.
  */
trait ServiceManifestsProvider { this: SetBindings =>

  final val manifests: Singleton[Set[PacBioComponentManifest]] = Singleton(
    () => set(AllServices).map(_.manifest))
}

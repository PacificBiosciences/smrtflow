package com.pacbio.secondary.lims.services

import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.services.ServiceComposer

/**
 * Example "Hello World" style service
 *
 * Delete this once the real services are up. This was mostly to test the RESTful endpoint.
 */
class HelloWorldService extends LimsService {

  val manifest = PacBioComponentManifest(
    toServiceId("smrtlink_hello_world"),
    name="Example Service",
    version="0.0.1",
    description="Example service to test that the LIMS Spray server works.")

  val PREFIX = "hello"

  // Maybe having the subreads resolving should be not under resolvers/*
  val routes =
    pathPrefix(PREFIX / "world") {
      pathEndOrSingleSlash {
        get {
          complete {
            "Hello World!"
          }
        }
      }
    }
}

trait HelloWorldProvider extends ServiceComposer {

  final val helloWorldService: Singleton[HelloWorldService] =
    Singleton(() => new HelloWorldService())

  addService(helloWorldService)
}
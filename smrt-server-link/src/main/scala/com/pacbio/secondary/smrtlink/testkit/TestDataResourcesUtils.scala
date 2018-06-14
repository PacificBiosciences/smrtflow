package com.pacbio.secondary.smrtlink.testkit

import com.pacbio.secondary.smrtlink.analysis.externaltools.{
  PacBioTestResources,
  PacBioTestResourcesLoader
}

import scala.util.Try

/**
  * This should only be used in Spec*.scala tests.
  *
  * Consumers should call in args(skipAll = PacBioTestResourcesLoader.isAvailable) to
  * disable the tests if the system is not configured with the files.json.
  */
trait TestDataResourcesUtils {
  lazy val testResources =
    Try(PacBioTestResourcesLoader.loadFromConfig())
      .getOrElse(PacBioTestResources(Nil))
}

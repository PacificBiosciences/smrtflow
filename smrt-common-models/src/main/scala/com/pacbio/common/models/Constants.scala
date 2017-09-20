package com.pacbio.common.models

import java.util.{Properties, UUID}
import collection.JavaConversions._
import collection.JavaConverters._

import scala.util.Try

/**
  *
  * Created by mkocher on 10/13/15.
  */
trait Constants {

  // Global DataSet "version" that every tool should use the write a DataSet
  final val DATASET_VERSION = "4.0.1"
  // Git SHA1 (previously Perforce CHANGELIST) that was used to generate the XSDs
  final val XSD_CHANGELIST = "8dbe5ce"

  private def getMajorMinorPatch(prop: Properties): Option[String] =
    Try { prop.getProperty("version").replace("-SNAPSHOT", "") }.toOption

  private def getGitShortSha(prop: Properties): Option[String] =
    Try { prop.getProperty("sha1").substring(0, 7) }.toOption

  // From the bamboo build number
  private def getBuildNumber(prop: Properties): Option[Int] =
    Try { prop.getProperty("buildNumber").toInt }.toOption

  private def getMetadata(shortSha: Option[String],
                          buildNumber: Option[Int]): String = {
    (shortSha, buildNumber) match {
      case (Some(sha), Some(n)) => s"+$n.$sha"
      case (None, Some(n)) => s"s+$n"
      case (Some(sha), None) => s"+$sha"
      case (None, None) => ""
    }
  }

  /**
    * The Smrtflow Version.
    *
    * Following the Semver format we have
    *
    * The "pacbio" build metadata contains
    *
    * {MAJOR}.{MINOR}.{TINY}+{bamboo_build_number}.{short_sha}
    *
    * This enables determining what the newest build is and communicates the
    * fundamental git data.
    *
    * If the bamboo build number is absent (or not an Int), the format will only
    * contain the short sha.
    *
    * {MAJOR}.{MINOR}.{TINY}+{short_sha}
    *
    * Note, that Semver only provides a mechanism for adding metadata using "+",
    * semver doesn't provide a standardized mechanism for determining if versions
    * are greater or less than other versions using the metadata. For this
    * semver, uses the prerelease tag + major, minor, patch for comparision.
    *
    *
    * Note, this still some hackery with removing the SNAPSHOT prerelease tag that
    * isn't completely correct to remove "SNAPSHOT"
    *
    * @param prop
    * @return
    */
  private def getSmrtFlowVersion(prop: Properties): String = {
    val majorMinorPath = getMajorMinorPatch(prop).getOrElse("0.0.0")
    val shortSha = getGitShortSha(prop)
    val buildNumber = getBuildNumber(prop)
    // This will include the "+"
    val metadata = getMetadata(shortSha, buildNumber)
    s"$majorMinorPath$metadata"
  }

  val SMRTFLOW_VERSION = {
    val files = getClass().getClassLoader().getResources("version.properties")
    if (files.hasMoreElements) {
      val in = files.nextElement().openStream()
      try {
        val prop = new Properties
        prop.load(in)
        getSmrtFlowVersion(prop)
      } finally {
        in.close()
      }
    } else {
      "unknown version"
    }
  }
}

object Constants extends Constants

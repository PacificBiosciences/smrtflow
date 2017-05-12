package com.pacbio.common

import collection.JavaConversions._
import collection.JavaConverters._

/**
  * Created by mkocher on 2/12/17.
  */
package object semver {
  import com.github.zafarkhaja.semver.Version

  // There doesn't appear to be well supported scala semver lib.
  // Adding a simple wrapper on top of the java one here
  case class SemVersion(major: Int, minor: Int, patch: Int, metadata: Option[String] = None, prereleaseTag: Option[String] = None) {

    private def toJVersion(): Version = {
      val builder = new Version.Builder(s"$major.$minor.$patch")
      // The java lib will handle "" the same as Null
      builder.setBuildMetadata(metadata.getOrElse(""))
      builder.setPreReleaseVersion(prereleaseTag.getOrElse(""))
      builder.build()
    }

    /**
      * Return a well formed semver string
      * @return
      */
    def toSemVerString(): String = {
      val builder = new Version.Builder(s"$major.$minor.$patch")
      // The java lib will handle "" the same as Null
      builder.setBuildMetadata(metadata.getOrElse(""))
      builder.setPreReleaseVersion(prereleaseTag.getOrElse(""))
      builder.build().toString
    }

    def gte(other: SemVersion): Boolean = {
      toJVersion().greaterThanOrEqualTo(other.toJVersion())
    }

    def equalTo(other: SemVersion) = toJVersion().equals(other.toJVersion())

    def lt(other: SemVersion) = toJVersion().lessThan(other.toJVersion())

    def gt(other: SemVersion) = toJVersion().greaterThan(other.toJVersion())

  }

  object SemVersion {

    // This isn't completely correct with the spec
    val orderBySemVersion:Ordering[SemVersion] = Ordering.by((v: SemVersion) => (v.major, v.minor, v.patch))

    def fromString(sx: String): SemVersion = {
      val semver = Version.valueOf(sx)

      SemVersion(
        semver.getMajorVersion,
        semver.getMinorVersion,
        semver.getPatchVersion,
        metadata = Some(semver.getBuildMetadata),
        prereleaseTag = Some(semver.getPreReleaseVersion))

    }
  }

}

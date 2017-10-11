package com.pacbio.secondary.smrtlink.models

import java.io.File
import java.nio.file.{Files, Path}

import com.pacbio.secondary.smrtlink.dependency.Singleton

class MimeTypes(highPriorityDetectors: Set[MimeTypeDetector],
                lowPriorityDetectors: Set[MimeTypeDetector]) {

  def apply(file: File): String = {
    var mime: Option[String] = None
    for (d <- highPriorityDetectors) {
      mime = d(file)
      if (mime.isDefined) return mime.get
    }
    for (d <- lowPriorityDetectors) {
      mime = d(file)
      if (mime.isDefined) return mime.get
    }
    Files.probeContentType(file.toPath)
  }

  def apply(path: Path): String = this(path.toFile)

  def apply(path: String): String = this(new File(path))
}

trait MimeTypeDetector {
  def apply(file: File): Option[String]
}

trait MimeTypeDetectors {
  import scala.collection.mutable

  private val highPriorityMimeTypeDetectors
    : mutable.Set[Singleton[MimeTypeDetector]] = mutable.HashSet.empty
  private val lowPriorityMimeTypeDetectors
    : mutable.Set[Singleton[MimeTypeDetector]] = mutable.HashSet.empty

  def addHighPriorityMimeTypeDetector(det: Singleton[MimeTypeDetector]): Unit =
    highPriorityMimeTypeDetectors.add(det)
  def addLowPriorityMimeTypeDetector(det: Singleton[MimeTypeDetector]): Unit =
    lowPriorityMimeTypeDetectors.add(det)

  // Include some basic mime type detectors automatically
  addLowPriorityMimeTypeDetector(Singleton(JsonMimeTypeDetector))

  val mimeTypes: Singleton[MimeTypes] = Singleton(
    () =>
      new MimeTypes(highPriorityMimeTypeDetectors.map(_()).toSet,
                    lowPriorityMimeTypeDetectors.map(_()).toSet))
}

// Files.probeContentType does not recognize JSON
object JsonMimeTypeDetector extends MimeTypeDetector {
  override def apply(file: File): Option[String] = file.getName match {
    // TODO(smcclellan): Probe file contents for valid JSON?
    case x if x.endsWith(".json") => Some("application/json")
    case _ => None
  }
}

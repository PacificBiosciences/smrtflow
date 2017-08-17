package com.pacbio.secondary.smrtlink

import java.io.File
import java.net.{URI, URL}
import java.nio.file.{Path, Paths}

import com.typesafe.scalalogging.LazyLogging


package object analysis {

  trait PacBioFileReader[T] extends LazyLogging {

    def loadFrom(file: File): T

    def loadFrom(path: String): T = {
      loadFrom(Paths.get(path).toFile)
    }

    def loadFrom(url: URL): T = {
      loadFrom(url.getPath)
    }

    def loadFrom(uri: URI): T = {
      loadFrom(uri.toURL)
    }
  }

  trait PacBioFileWriter[T] extends LazyLogging {
    def writeFile(ds: T, path: Path): Path

  }
}

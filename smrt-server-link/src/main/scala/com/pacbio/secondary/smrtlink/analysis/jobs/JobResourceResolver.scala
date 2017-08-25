package com.pacbio.secondary.smrtlink.analysis.jobs

import java.io.IOException
import java.nio.file.{Paths, Files, Path}

import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.RunnableJobWithId
import com.typesafe.scalalogging.LazyLogging


/**
 * Interface that resolves a job to a directory on the file systema
 */
trait JobResourceResolver extends LazyLogging {

  // This should resolve the root directory
  def resolve(jobId: Int): Path

  def createIfNecessary(p: Path): Path = {
    // Maybe this approach isn't the best idea.
    // Use a ask-for-forgiveness model
    try {
      if (!Files.exists(p)) {
        Files.createDirectory(p)
      }
    } catch {
      case ioe: IOException =>
        logger.warn(s"IOException. Failed to create directory ${ioe.getMessage}")
      case e: Exception =>
        logger.error(s"Failed to create directory ${e.getMessage}")
    }
    p
  }
}

/**
 * Resolves jobs to the Pacbio RS-era style job directory structure
 *
 * 7 -> 000/000007
 *
 * 12345 -> 001/012345
 *
 *
 *
 * @param rootDir
 */
class PacBioIntJobResolver(rootDir: Path) extends JobResourceResolver with LazyLogging {

  private def toJobBasePrefix(n: Int) = {
    toJobDirString(n) slice(0, 3)
  }

  private def toJobDirString(n: Int) = {
    val ns = n.toString
    6 - ns.length match {
      case 0 => ns
      case _ => (0 to 5 - ns.length).foldLeft("")((a, b) => a + "0") + ns
    }
  }

  // Full
  private def toJobDir(n: Int) = {
    s"${toJobBasePrefix(n)}/${toJobDirString(n)}"
  }

  def resolve(jobId: Int) = {
    val jobDir = toJobDir(jobId)
    val baseDir = toJobBasePrefix(jobId)

    val basePath = createIfNecessary(rootDir.resolve(baseDir))
    val jobPath = createIfNecessary(rootDir.resolve(jobDir))

    jobPath
  }
}

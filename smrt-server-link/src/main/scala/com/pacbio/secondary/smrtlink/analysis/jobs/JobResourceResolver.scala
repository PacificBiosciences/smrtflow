package com.pacbio.secondary.smrtlink.analysis.jobs

import java.io.IOException
import java.nio.file.{FileAlreadyExistsException, Files, Path, Paths}

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
      case e: FileAlreadyExistsException =>
        logger.warn(s"Directory already exists. Skipping creation of $p")
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

  private def toJobBasePrefix(n: Int):String = toJobDirString(n) slice(0, 3)

  private def toJobDirString(n: Int):String = {
    val ns = n.toString
    6 - ns.length match {
      case 0 => ns
      case _ => (0 to 5 - ns.length).foldLeft("")((a, b) => a + "0") + ns
    }
  }

  // Full
  private def toJobDir(n: Int):String = s"${toJobBasePrefix(n)}/${toJobDirString(n)}"


  /**
    * This should always be wrapped in a Try
    *
    * @throws IOException
    * @param jobId
    * @return
    */
  def resolve(jobId: Int):Path = {
    val jobDir = toJobDir(jobId)
    val baseDir = toJobBasePrefix(jobId)

    // The base dir for a block of jobs (e.g., 012) could
    // potentially be created different job instances, hence we'll
    // ignore the exception here
    val basePath = createIfNecessary(rootDir.resolve(baseDir))

    // full job path
    val fullJobPath = rootDir.resolve(jobDir)

    // If the job path already exists, this means there's jobs
    // trying to "resuse" (and overwrite previous job's output)
    // Here we raise if the job directory already exists.
    if (Files.exists(fullJobPath)) {
      throw new IOException(s"Job $jobId directory already exists. This could be from a previous install $fullJobPath")
    } else {
      Files.createDirectory(fullJobPath)
    }

    fullJobPath
  }
}

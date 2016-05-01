package com.pacbio.secondary.analysis.jobs

import java.io.IOException
import java.nio.file.{Paths, Files, Path}

import com.pacbio.secondary.analysis.jobs.JobModels.RunnableJobWithId
import com.typesafe.scalalogging.LazyLogging


/**
 * Interface that resolves a job to a directory on the file systema
 */
trait JobResourceResolver extends LazyLogging {
  // This should resolve the root directory for a given jobtype
  def resolve(runnableJobWithId: RunnableJobWithId): Path

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
 * Determines where a jobOptions will be written to.
 *
 * Create the jobtype subdir to the root location and
 * resolved the job directory based on the job UUID
 *
 * Created by mkocher on 5/8/15.
 */

/**
 * Resolve Paths for jobs to be written to
 *
 * @param rootDir Root path for jobs to be written to. 
 */
class SimpleUUIDJobResolver(rootDir: Path) extends JobResourceResolver {
  type In = CoreJob

  def resolve(runnableJobWithId: RunnableJobWithId) = {
    // Create the root jobOptions type dir if it doesn't exist.
    // This is awkward
    val basename = "jobtypes-" + runnableJobWithId.job.jobOptions.toJob.jobTypeId.id
    val jobTypeRoot = createIfNecessary(rootDir.resolve(basename))
    val jobOutputDir = createIfNecessary(jobTypeRoot.resolve(runnableJobWithId.job.uuid.toString))
    jobOutputDir
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

  def toJobBasePrefix(n: Int) = {
    toJobDirString(n) slice(0, 3)
  }

  def toJobDirString(n: Int) = {
    val ns = n.toString
    6 - ns.length match {
      case 0 => ns
      case _ => (0 to 5 - ns.length).foldLeft("")((a, b) => a + "0") + ns
    }
  }

  // Full
  def toJobDir(n: Int) = {
    s"${toJobBasePrefix(n)}/${toJobDirString(n)}"
  }

  def resolve(runnableJobWithId: RunnableJobWithId) = {
    val jobDir = toJobDir(runnableJobWithId.id)
    val baseDir = toJobBasePrefix(runnableJobWithId.id)

    val basePath = createIfNecessary(rootDir.resolve(baseDir))
    val jobPath = createIfNecessary(rootDir.resolve(jobDir))

    jobPath
  }
}

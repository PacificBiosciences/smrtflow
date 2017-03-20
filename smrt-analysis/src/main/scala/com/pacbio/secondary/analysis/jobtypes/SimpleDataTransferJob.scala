package com.pacbio.secondary.analysis.jobtypes

import java.nio.file.{Files, Paths}

import com.pacbio.secondary.analysis.jobs._
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.tools.timeUtils
import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime}

// DataTransfer
case class SimpleDataTransferOptions(src: String, dest: String, override val projectId: Int = 1) extends BaseJobOptions {
  def toJob = new SimpleDataTransferJob(this)
}


/**
 * Simplest possible data-transfer service. Taken from the zero-analysis primary POC.
 *
 * Created by mkocher on 4/27/15.
 */
class SimpleDataTransferJob(opts: SimpleDataTransferOptions)
  extends BaseCoreJob(opts: SimpleDataTransferOptions)
  with timeUtils{

  type Out = ResultSuccess
  val jobTypeId = JobTypeId("simple_data_transfer")

  def run(job: JobResourceBase, resultsWriter: JobResultWriter): Either[ResultFailed, ResultSuccess] = {
    val startedAt = JodaDateTime.now()

    val srcP = Paths.get(opts.src)
    val destP = Paths.get(opts.dest)

    if (Files.isDirectory(srcP)) {
      logger.info(s"copying directory $srcP to $destP")
      FileUtils.copyDirectory(srcP.toFile, destP.toFile)
    } else {
      logger.info(s"copying file from $srcP to $destP")
      FileUtils.copyFile(srcP.toFile, destP.toFile)
    }
    //
    val msg = s"completed transferring files from: ${srcP.toString} to ${destP.toString}"
    logger.info(msg)

    val runTimeSec = computeTimeDeltaFromNow(startedAt)
    Right(ResultSuccess(job.jobId, jobTypeId.toString, "Completed running", runTimeSec, AnalysisJobStates.SUCCESSFUL, host))
  }


}

package com.pacbio.secondary.analysis

import java.io.FileWriter
import java.nio.file.{Path, Paths, Files}
import java.util.UUID

import com.pacbio.secondary.analysis.tools.timeUtils
import org.apache.commons.io.FileUtils

import scala.sys.process._
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}

import scala.collection.JavaConversions._

/**
  * Utils to Shell out to external Process
  * Created by mkocher on 9/26/15.
  *
  * This needs to be rethought and cleanedup. There's too many degenerative models here.
  *
  */
package object externaltools {

  sealed trait ExternalCmdResult {
    val cmd: Seq[String]
    val runTime: Long
  }

  case class ExternalCmdSuccess(cmd: Seq[String], runTime: Long) extends ExternalCmdResult

  case class ExternalCmdFailure(cmd: Seq[String], runTime: Long, msg: String) extends Exception(msg) with ExternalCmdResult

  trait ExternalToolsUtils extends LazyLogging with timeUtils {

    def runSimpleCmd(cmd: Seq[String]): Option[ExternalCmdFailure] = {
      runCmd(cmd) match {
        case Right(_) => None
        case Left(ex) => Some(ex)
      }
    }

    def runCmd(cmd: Seq[String]): Either[ExternalCmdFailure, ExternalCmdSuccess] = {
      val jobId = UUID.randomUUID()
      // Add a cleanup if the cmd was successful
      val fout = Files.createTempFile(s"cmd-$jobId", "stdout")
      val ferr = Files.createTempFile(s"cmd-$jobId", "stderr")
      runCmd(cmd, fout.toAbsolutePath, ferr.toAbsolutePath)
    }

    /**
     * Core util to run external command
     *
     * @param cmd
     * @param stdout
     * @param stderr
     * @return
     */
    def runUnixCmd(cmd: Seq[String], stdout: Path, stderr: Path): (Int, String) = {

      val startedAt = JodaDateTime.now()
      val fout = new FileWriter(stdout.toAbsolutePath.toString, true)
      val ferr = new FileWriter(stderr.toAbsolutePath.toString, true)

      // Write the subprocess standard error to propagate error message up.
      val errStr = new StringBuilder

      val pxl = ProcessLogger(
        (o: String) => {
          fout.write(o + "\n")
        },
        (e: String) => {
          logger.error(e + "\n")
          ferr.write(e + "\n")
          errStr.append(e + "\n")
        }
      )

      logger.info(s"Starting cmd $cmd")
      val rcode = Process(cmd).!(pxl)
      val completedAt = JodaDateTime.now()
      val runTime = computeTimeDelta(completedAt, startedAt)
      logger.info(s"completed running with exit-code $rcode in $runTime sec. Command -> $cmd")

      if (rcode != 0) {
        val emsg = s"Cmd $cmd failed with exit code $rcode"
        logger.error(s"completed running with exit-code $rcode in $runTime sec. Command -> $cmd")
        ferr.write(emsg)
        errStr.append(emsg + "\n")
      }

      fout.close()
      ferr.close()

      (rcode, errStr.toString())

    }

    def runCmd(cmd: Seq[String], stdout: Path, stderr: Path): Either[ExternalCmdFailure, ExternalCmdSuccess] = {
      val startedAt = JodaDateTime.now()
      val (exitCode, errorMessage) = runUnixCmd(cmd, stdout, stderr)
      val runTime = computeTimeDelta(JodaDateTime.now(), startedAt)
      exitCode match {
        case 0 => Right(ExternalCmdSuccess(cmd, runTime))
        case x => Left(ExternalCmdFailure(cmd, runTime, errorMessage))
      }
    }


    /**
     * Resolve commandline exe path to absolute path.
     *
     * Filters out "." from path.
     *
     * @param cmd base name of exe (Example "samtools")
     * @return
     */
    def which(cmd: String): Option[Path] = {
      // The runCmd needs to resolve the Exe to provide a good error
      // If the external tool is not found in the path
      System.getenv("PATH")
        .split(":")
        .filter(_ != ".")
        .map(a => Paths.get(a).toAbsolutePath.resolve(cmd))
        .find(x => Files.exists(x))
    }

    def isExeAvailable(args: Seq[String]): Boolean = {
      Try {
        runCmd(args).isRight
      } match {
        case Success(b) => b
        case Failure(err) => false
      }
    }
  }

  object ExternalToolsUtils extends ExternalToolsUtils

}

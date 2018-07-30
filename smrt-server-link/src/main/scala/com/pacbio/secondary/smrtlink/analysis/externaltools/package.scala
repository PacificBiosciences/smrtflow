package com.pacbio.secondary.smrtlink.analysis

import java.io.{File, FileWriter}
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import org.apache.commons.io.FileUtils

import scala.sys.process._
import scala.util.Try
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}

/**
  * Utils to Shell out to external Process
  * Created by mkocher on 9/26/15.
  *
  * There's too many degenerative models here. This needs to be rethought and
  * cleaned up and while also improve the configurability of subprocess calls.
  *
  */
package object externaltools {

  sealed trait ExternalCmdResult {
    val cmd: Seq[String]
    val runTime: Long
    val exitCode: Int
  }

  case class ExternalCmdSuccess(cmd: Seq[String], runTime: Long)
      extends ExternalCmdResult {
    override val exitCode: Int = 0
  }

  case class ExternalCmdFailure(cmd: Seq[String],
                                runTime: Long,
                                msg: String,
                                override val exitCode: Int = 1)
      extends Exception(msg)
      with ExternalCmdResult {
    def summary: String =
      s"Failed to run Command with exit code $exitCode in $runTime sec Error: $msg"
  }

  trait ExternalToolsUtils extends LazyLogging with timeUtils {

    val SUFFIX_STDOUT = "stdout"
    val SUFFIX_STDERR = "stderr"

    private def toTmp(suffix: String, uuid: Option[UUID] = None): Path = {
      val ix = uuid.getOrElse(UUID.randomUUID())
      Files.createTempFile(s"cmd-$ix", suffix).toAbsolutePath
    }
    private def toWriter(p: Path) =
      new FileWriter(p.toAbsolutePath.toString, true)

    private def cleanUp(files: Seq[Path]): Unit = {
      files
        .map(_.toFile)
        .foreach(FileUtils.deleteQuietly)
    }

    /**
      * This is a similar model to python's checkcall in Popen.
      *
      * This will ignore writing to stderr, stdout, or
      * logging.
      *
      * @param cmd Command to run
      * @return
      */
    def runCheckCall(cmd: Seq[String]): Option[ExternalCmdFailure] = {
      val startedAt = JodaDateTime.now()

      val tmpOut = toTmp("stdout")
      val tmpErr = toTmp("stderr")

      val fout = toWriter(tmpOut)
      val ferr = toWriter(tmpErr)

      def runAndCleanUp(cmd: Seq[String], processLogger: ProcessLogger)
        : Either[ExternalCmdFailure, ExternalCmdSuccess] = {
        val result =
          runUnixCmd(cmd, tmpOut, tmpErr, processLogger = Some(processLogger))
        cleanUp(Seq(tmpOut, tmpErr))
        result
      }

      val processLogger = toProcessLoggerFile(fout, ferr)

      runAndCleanUp(cmd, processLogger) match {
        case Right(_) => None
        case Left(cmdFailure) => Some(cmdFailure)
      }
    }

    /**
      * Run External Command and cleanup (deleted) stderr and stdout
      *
      * This should be used very sparingly as the stdout and stderr are deleted.
      *
      */
    def runCmd(
        cmd: Seq[String]): Either[ExternalCmdFailure, ExternalCmdSuccess] = {
      val cmdId = UUID.randomUUID()
      val stdout = toTmp(SUFFIX_STDOUT, Some(cmdId))
      val stderr = toTmp(SUFFIX_STDERR, Some(cmdId))
      val tmpFiles = Seq(stdout, stderr)

      val results = runUnixCmd(cmd,
                               toTmp(SUFFIX_STDOUT, Some(cmdId)),
                               toTmp(SUFFIX_STDERR, Some(cmdId)))
      cleanUp(tmpFiles)
      results
    }
    private def toProcessLoggerFile(fout: FileWriter,
                                    ferr: FileWriter): ProcessLogger =
      ProcessLogger(
        (o: String) => {
          fout.write(o + "\n")
        },
        (e: String) => {
          logger.error(e + "\n")
          ferr.write(e + "\n")
        }
      )

    private def toProcessLogger(fout: FileWriter,
                                ferr: FileWriter,
                                errorMsg: StringBuilder): ProcessLogger = {
      ProcessLogger(
        (o: String) => {
          fout.write(o + "\n")
        },
        (e: String) => {
          logger.error(e + "\n")
          ferr.write(e + "\n")
          errorMsg.append(e + "\n")
        }
      )
    }

    /**
      * Core util to run external command
      *
      * @param cmd      Command as a seq of Strings
      * @param stdout   Path to stdout
      * @param stderr   Path to Stderr
      * @param extraEnv Env to be added to the process env
      * @param logErrors Will write errors to the system configured log
      * @return
      */
    def runUnixCmd(cmd: Seq[String],
                   stdout: Path,
                   stderr: Path,
                   extraEnv: Option[Map[String, String]] = None,
                   cwd: Option[File] = None,
                   processLogger: Option[ProcessLogger] = None,
                   logErrors: Boolean = true)
      : Either[ExternalCmdFailure, ExternalCmdSuccess] = {

      val startedAt = JodaDateTime.now()

      val getWriter = (p: Path) =>
        new FileWriter(p.toAbsolutePath.toString, true)

      val fout = getWriter(stdout)
      val ferr = getWriter(stderr)

      def cleanUp(): Unit = {
        Seq(fout, ferr).foreach { f =>
          f.flush()
          f.close()
        }
      }

      // Write the subprocess standard error to propagate error message up.
      val errStr = new StringBuilder

      val pxl = processLogger.getOrElse(toProcessLogger(fout, ferr, errStr))

      logger.info(s"Starting cmd $cmd with process logger $pxl")

      val px = extraEnv
        .map(x => Process(cmd, cwd = None, extraEnv = x.toSeq: _*))
        .getOrElse(Process(cmd, cwd = None))

      val rcode = px.!(pxl)

      val completedAt = JodaDateTime.now()
      val runTime = computeTimeDelta(completedAt, startedAt)

      def toM(sx: String, exitCode: Int) =
        s"running with exit-code $exitCode in $runTime sec. Command -> $cmd"

      def logResult(sx: String) = logger.info(sx)

      def logFailedResult(sx: String) = {
        if (logErrors) {
          logger.error(sx)
          ferr.write(sx)
        } else {
          logResult(sx)
        }
      }

      rcode match {
        case 0 => logResult(toM("Successfully completed ", 0))
        case n => logFailedResult(toM("Failed", n))
      }

      cleanUp()

      rcode match {
        case 0 => Right(ExternalCmdSuccess(cmd, runTime))
        case n => Left(ExternalCmdFailure(cmd, runTime, errStr.toString(), n))
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
      System
        .getenv("PATH")
        .split(":")
        .filter(_ != ".")
        .map(a => Paths.get(a).toAbsolutePath.resolve(cmd))
        .find(x => Files.exists(x))
    }

    def isExeAvailable(args: Seq[String]): Boolean =
      Try { runCheckCall(args).isEmpty }.getOrElse(false)
  }

  object ExternalToolsUtils extends ExternalToolsUtils

}

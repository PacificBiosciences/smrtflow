package com.pacbio.secondary.smrtlink.analysis.jobs

import java.io._
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import java.util.zip._

import scala.util.Try

import org.apache.commons.io.{FileUtils, FilenameUtils}
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._

import com.pacificbiosciences.pacbiodatasets.DataSetType
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetFileUtils
import com.pacbio.secondary.smrtlink.testkit.MockFileUtils
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetExporter
import JobModels._

trait JobUtils extends SecondaryJobJsonProtocol with LazyLogging {

  private def writeDataStore(ds: PacBioDataStore, out: Path): Path = {
    FileUtils.writeStringToFile(out.toFile, ds.toJson.prettyPrint, "UTF-8")
    out
  }

  private def processDataStore(f: (PacBioDataStore => PacBioDataStore),
                               dataStorePath: Path,
                               dsOutPath: Path): Path = {
    val ds = FileUtils
      .readFileToString(dataStorePath.toFile, "UTF-8")
      .parseJson
      .convertTo[PacBioDataStore]

    writeDataStore(f(ds), dsOutPath)
  }

  /**
    * Load a datastore JSON and convert all paths to relative to rootPath
    *
    * @param rootPath      base job directory to which all paths should be relative
    * @param dataStorePath input path to datastore JSON
    * @param dsOutPath     output datastore
    */
  protected def relativizeDataStore(rootPath: Path,
                                    dataStorePath: Path,
                                    dsOutPath: Path): Path = {

    processDataStore((ds: PacBioDataStore) => ds.relativize(rootPath),
                     dataStorePath,
                     dsOutPath)
  }

  /**
    * Load a datastore JSON and convert paths to absolute
    *
    * @param rootPath      base job directory to which input paths are relative
    * @param dataStorePath in path to datastore JSON
    * @param dsOutPath     output Path to datastore JSON
    */
  protected def absolutizeDataStore(rootPath: Path,
                                    dataStorePath: Path,
                                    dsOutPath: Path): Path = {
    processDataStore((ds: PacBioDataStore) => ds.absolutize(rootPath),
                     dataStorePath,
                     dsOutPath)
  }

  protected def getDataStore(rootPath: Path): Option[PacBioDataStore] = {

    Seq("datastore.json", "workflow/datastore.json")
      .map(n => rootPath.resolve(n))
      .find(_.toFile.exists())
      .map { p =>
        FileUtils
          .readFileToString(p.toFile, "UTF-8")
          .parseJson
          .convertTo[PacBioDataStore]
          .relativize(rootPath)
      }
  }

  /**
    * Collapses a list of core job states into a single state that captures the state
    * of the MultiJob.
    *
    * Any core job in Submitted, or Running state will translate into RUNNING at
    * the mulit-job level
    *
    * @return
    */
  private def reduceJobStates(
      s1: AnalysisJobStates.JobStates,
      s2: AnalysisJobStates.JobStates): AnalysisJobStates.JobStates = {
    (s1, s2) match {
      case (AnalysisJobStates.CREATED, AnalysisJobStates.CREATED) =>
        AnalysisJobStates.CREATED
      case (AnalysisJobStates.RUNNING, AnalysisJobStates.RUNNING) =>
        AnalysisJobStates.RUNNING
      case (AnalysisJobStates.SUCCESSFUL, AnalysisJobStates.SUCCESSFUL) =>
        AnalysisJobStates.SUCCESSFUL
      case (AnalysisJobStates.SUBMITTED, AnalysisJobStates.SUBMITTED) =>
        AnalysisJobStates.SUBMITTED
      case (AnalysisJobStates.SUBMITTED, AnalysisJobStates.CREATED) =>
        AnalysisJobStates.SUBMITTED
      case (AnalysisJobStates.CREATED, AnalysisJobStates.SUBMITTED) =>
        AnalysisJobStates.RUNNING
      case (AnalysisJobStates.SUBMITTED, AnalysisJobStates.SUCCESSFUL) =>
        AnalysisJobStates.SUBMITTED
      case (AnalysisJobStates.SUCCESSFUL, AnalysisJobStates.SUBMITTED) =>
        AnalysisJobStates.SUBMITTED
      // This is unclear what this means. Defaulting to Failed
      case (AnalysisJobStates.UNKNOWN, AnalysisJobStates.UNKNOWN) =>
        AnalysisJobStates.FAILED
      // If any child job has failed, then fail all jobs
      case (AnalysisJobStates.FAILED, _) => AnalysisJobStates.FAILED
      case (_, AnalysisJobStates.FAILED) => AnalysisJobStates.FAILED
      // If any child job has been Terminated, then mark the MultiJob as failed
      case (AnalysisJobStates.TERMINATED, _) => AnalysisJobStates.FAILED
      case (_, AnalysisJobStates.TERMINATED) => AnalysisJobStates.FAILED
      // If
      case (AnalysisJobStates.RUNNING, _) => AnalysisJobStates.RUNNING
      case (_, AnalysisJobStates.RUNNING) => AnalysisJobStates.RUNNING
      case (_, AnalysisJobStates.CREATED) => AnalysisJobStates.RUNNING
      case (AnalysisJobStates.CREATED, _) => AnalysisJobStates.RUNNING
      case (_, _) =>
        logger.error(
          s"Unclear mapping of MultiJob state from $s1 and $s2. Defaulting to ${AnalysisJobStates.FAILED}")
        AnalysisJobStates.FAILED
    }
  }

  private def reduceFromSingleState(
      state: AnalysisJobStates.JobStates): AnalysisJobStates.JobStates = {
    state match {
      case AnalysisJobStates.CREATED => AnalysisJobStates.RUNNING
      case AnalysisJobStates.RUNNING => AnalysisJobStates.RUNNING
      case AnalysisJobStates.SUBMITTED => AnalysisJobStates.RUNNING
      case AnalysisJobStates.FAILED => AnalysisJobStates.FAILED
      case AnalysisJobStates.TERMINATED => AnalysisJobStates.FAILED
      case AnalysisJobStates.UNKNOWN => AnalysisJobStates.FAILED
      case AnalysisJobStates.SUCCESSFUL => AnalysisJobStates.SUCCESSFUL
    }
  }

  /**
    * Collapse the Children job states into a reflection of the overall state of the MultiJob
    */
  def determineMultiJobState(
      states: Seq[AnalysisJobStates.JobStates],
      default: AnalysisJobStates.JobStates): AnalysisJobStates.JobStates = {
    states match {
      case Nil => default
      case s1 :: Nil => reduceFromSingleState(s1)
      case _ => states.reduce(reduceJobStates)
    }
  }
}

object JobUtils extends JobUtils

class JobExporter(job: EngineJob, zipPath: Path)
    extends DataSetExporter(zipPath)
    with DataSetFileUtils
    with JobUtils
    with SecondaryJobJsonProtocol
    with LazyLogging {

  case class JobExportSummary(nBytes: Long)

  /**
    * Recursively export the contents of an arbitrary directory, relative to a
    * base path (defaults to the starting path)
    * @param path  directory path to export
    * @param basePath  root path, archive paths will be relative to this
    */
  protected def exportPath(path: Path, basePath: Path): Long = {
    val f = path.toFile
    if (f.isFile) {
      if (FilenameUtils.getName(path.toString) == "datastore.json") {
        val tmpOutPath = Files.createTempFile(s"datastore-tmp", ".json")
        val ds = relativizeDataStore(basePath, path, tmpOutPath)
        val n = exportFile(path, basePath, Some(ds))
        FileUtils.deleteQuietly(tmpOutPath.toFile)
        n
      } else if (path.toString.endsWith("set.xml")) {
        Try { getDataSetMiniMeta(path) }.toOption
          .map { m =>
            val destPath = basePath.relativize(path)
            if (haveFiles contains destPath.toString) {
              logger.warn(s"Skipping duplicate entry ${destPath.toString}"); 0L
            } else {
              writeDataSet(path, destPath, m.metatype, Some(basePath))
            }
          }
          .getOrElse(exportFile(path, basePath))
      } else {
        exportFile(path, basePath)
      }
    } else if (f.isDirectory) {
      if (path.toString.endsWith("/entry-points")) {
        logger.warn(s"Skipping entry points in ${path.toString}"); 0L
      } else {
        logger.info(s"Exporting subdirectory ${path.toString}...")
        f.listFiles.map(fn => exportPath(fn.toPath, basePath)).sum
      }
    } else {
      logger.warn(s"Skipping ${path.toString}"); 0L
    }
  }

  private def convertEntryPointPath(entryPoint: BoundEntryPoint,
                                    jobPath: Path): BoundEntryPoint = {
    val dsMeta = getDataSetMiniMeta(entryPoint.path)
    val ext = dsMeta.metatype.fileType.fileExt
    // this is consistent with how SubreadSet entry points are written
    // in upstream jobs
    val outputPath = Paths.get(s"entry-points/${dsMeta.uuid.toString}.$ext")
    entryPoint.copy(path = outputPath)
  }

  private def convertEntryPointPaths(entryPoints: Seq[BoundEntryPoint],
                                     jobPath: Path): Seq[BoundEntryPoint] = {
    entryPoints.map(e => convertEntryPointPath(e, jobPath))
  }

  /**
    * Write the entry points to a subdirectory in the zip, along with a JSON
    * file referencing them
    */
  protected def exportEntryPoints(entryPoints: Seq[BoundEntryPoint],
                                  jobPath: Path): Long = {
    if (entryPoints.isEmpty) return 0L
    val epsOut = convertEntryPointPaths(entryPoints, jobPath)
    entryPoints
      .zip(epsOut)
      .map {
        case (e, o) =>
          if (!e.path.toFile.exists) {
            logger.warn(
              s"Skipping entry point ${e.entryId}:${e.path.toString} because the path no longer exists")
            0L
          } else {
            Try { getDataSetMiniMeta(e.path) }.toOption
              .map { m =>
                if (haveFiles contains o.path.toString) {
                  logger.warn(s"Skipping duplicate entry ${o.path.toString}")
                  0L
                } else {
                  val epRootPath = e.path.getParent
                  if (epRootPath.toString.endsWith("entry-points")) {
                    writeDataSet(e.path,
                                 o.path,
                                 m.metatype,
                                 Some(epRootPath.getParent))
                  } else {
                    writeDataSet(e.path, o.path, m.metatype, Some(jobPath))
                  }
                }
              }
              .getOrElse(exportFile(o.path, Paths.get(""), Some(e.path)))
          }
      }
      .sum
  }

  /**
    * Package the entire job directory into a zipfile.
    */
  def toZip(entryPoints: Seq[BoundEntryPoint] = Seq.empty[BoundEntryPoint],
            jobEvents: Seq[JobEvent] = Seq.empty[JobEvent])
    : Try[JobExportSummary] = {
    val jobPath = Paths.get(job.path)
    if (jobPath.toFile.isFile) {
      throw new RuntimeException(s"${jobPath.toString} is not a directory")
    }
    val entryPointsOut = convertEntryPointPaths(entryPoints, jobPath)
    val datastore = getDataStore(jobPath)
    val manifest =
      ExportJobManifest(job, entryPointsOut, datastore, Some(jobEvents))
    val manifestFile = Files.createTempFile("export-job-manifest", ".json")
    FileUtils.writeStringToFile(manifestFile.toFile,
                                manifest.toJson.prettyPrint,
                                "UTF-8")
    val nBytes: Long = exportPath(jobPath, jobPath) +
      exportEntryPoints(entryPoints, jobPath) +
      exportFile(jobPath.resolve(JobConstants.OUTPUT_EXPORT_MANIFEST_JSON),
                 jobPath,
                 Some(manifestFile))
    out.close
    FileUtils.deleteQuietly(manifestFile.toFile)
    Try { JobExportSummary(nBytes) }
  }
}

object ExportJob {
  def apply(job: EngineJob,
            zipFileName: Path,
            entryPoints: Seq[BoundEntryPoint] = Seq.empty[BoundEntryPoint],
            events: Seq[JobEvent] = Seq.empty[JobEvent]) = {
    new JobExporter(job, zipFileName).toZip(entryPoints, events)
  }
}

trait JobImportUtils
    extends SecondaryJobJsonProtocol
    with JobUtils
    with LazyLogging {
  protected val BUFFER_SIZE = 2048

  case class JobImportSummary(nFiles: Int)

  /**
    * Decompress a zip file containing a job
    */
  def expandJob(zipFile: Path, jobPath: Path): Try[JobImportSummary] = Try {
    val zis = new ZipInputStream(new FileInputStream(zipFile.toFile))
    //get the zipped file list entry
    var ze = Option(zis.getNextEntry())
    var nFiles = 0
    while (ze.isDefined) {
      val fileName = ze.get.getName()
      val newFile = jobPath.resolve(fileName).toFile
      logger.debug(s"Deflating ${newFile.getAbsoluteFile}")
      Paths.get(newFile.getParent).toFile.mkdirs
      val fos = new FileOutputStream(newFile)
      val buffer = new Array[Byte](BUFFER_SIZE)
      var len = 0
      while ({ len = zis.read(buffer); len > 0 }) {
        fos.write(buffer, 0, len)
      }
      fos.close()
      if (FilenameUtils
            .getName(fileName) == JobConstants.OUTPUT_DATASTORE_JSON) {
        logger.info(s"Updating paths in ${fileName}")
        absolutizeDataStore(jobPath, newFile.toPath, newFile.toPath)
      }
      ze = Option(zis.getNextEntry())
      nFiles += 1
    }
    zis.closeEntry
    zis.close
    JobImportSummary(nFiles)
  }

  /**
    * Retrieve the manifest from an exported job ZIP file.
    */
  def getManifest(zipFile: Path): ExportJobManifest = {
    val zf = new ZipFile(zipFile.toFile)
    Option(zf.getEntry("export-job-manifest.json"))
      .map { ze =>
        val buffer = new Array[Byte](ze.getSize().toInt)
        val zis = zf.getInputStream(ze)
        zis.read(buffer, 0, ze.getSize().toInt)
        zis.close
        new String(buffer).parseJson.convertTo[ExportJobManifest]
      }
      .getOrElse {
        throw new IllegalArgumentException(
          s"Can't read export-job-manifest.json in $zipFile.  Only jobs exported through the SMRT Link export-jobs service may be imported.")
      }
  }
}

// Put all the general utils for writing a mock pbsmrtpipe jobOptions, then refactor
// into real "jobOptions" level utils (e.g., progress updating, writing entry points, settings, options, etc...)
trait CoreJobUtils extends LazyLogging with SecondaryJobJsonProtocol {

  def setupJobResourcesAndCreateDirs(outputDir: Path): AnalysisJobResources = {

    if (!Files.isDirectory(outputDir)) {
      logger.error(s"output dir is not a Dir ${outputDir.toString}")
    }

    def toPx(x: Path, name: String): Path = {
      val p = x.resolve(name)
      if (!Files.exists(p)) {
        logger.info(s"Creating dir $p")
        Files.createDirectories(p)
      }
      p
    }

    val toP = toPx(outputDir, _: String)

    // This is where the datastore.json will be written. Keep this
    val workflowPath = toP("workflow")

    def relToWorkflow(sx: String): Path = workflowPath.resolve(sx)

    // Don't create these for non-pbsmrtpipe jobs. This makes little sense to try to adhere to this interface
    val tasksPath = outputDir.resolve("tasks")
    val htmlPath = outputDir.resolve("html")
    val logPath = outputDir.resolve("logs")

    logger.debug(s"creating resources in ${outputDir.toAbsolutePath}")
    val r = AnalysisJobResources(
      outputDir,
      tasksPath,
      workflowPath,
      logPath,
      htmlPath,
      relToWorkflow("datastore.json"),
      relToWorkflow("entry-points.json"),
      relToWorkflow("jobOptions-report.json")
    )

    logger.info(s"Successfully created resources")
    r
  }

  def toDatastore(jobResources: AnalysisJobResources,
                  files: Seq[DataStoreFile]): PacBioDataStore =
    PacBioDataStore.fromFiles(files)

  def writeStringToFile(s: String, path: Path): Path = {
    // for backward compatibility
    FileUtils.writeStringToFile(path.toFile, s)
    path
  }

  def writeDataStore(ds: PacBioDataStore, path: Path): Path = {
    FileUtils.writeStringToFile(path.toFile, ds.toJson.prettyPrint.toString)
    path
  }

  def toDataStoreFile[T <: DataSetType](ds: T,
                                        output: Path,
                                        description: String,
                                        sourceId: String): DataStoreFile = {
    val uuid = UUID.fromString(ds.getUniqueId)
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt
    DataStoreFile(
      uuid,
      sourceId,
      ds.getMetaType,
      output.toFile.length,
      createdAt,
      modifiedAt,
      output.toAbsolutePath.toString,
      isChunked = false,
      Option(ds.getName).getOrElse("PacBio DataSet"),
      description
    )
  }

  /**
    * This will a real fasta file that can be used
    *
    * @return
    */
  def toMockFastaDataStoreFile(rootDir: Path): DataStoreFile = {
    val createdAt = JodaDateTime.now()
    val uuid = UUID.randomUUID()
    val nrecords = 100
    val p = rootDir.resolve(s"mock-${uuid.toString}.fasta")
    MockFileUtils.writeMockFastaFile(nrecords, p)
    DataStoreFile(
      uuid,
      "mock-pbsmrtpipe",
      FileTypes.FASTA.fileTypeId,
      p.toFile.length(),
      createdAt,
      createdAt,
      p.toAbsolutePath.toString,
      isChunked = false,
      "Mock Fasta",
      s"Mock Fasta file generated with $nrecords records"
    )
  }

  def toMockDataStoreFiles(rootDir: Path): Seq[DataStoreFile] = {
    (0 until 4).map(x => toMockFastaDataStoreFile(rootDir))
  }

  def writeEntryPoints(entryPoints: Seq[BoundEntryPoint], path: Path): Path = {
    writeStringToFile(entryPoints.toJson.toString, path)
  }
}

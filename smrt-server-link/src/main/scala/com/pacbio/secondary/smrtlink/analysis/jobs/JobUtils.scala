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
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetFileUtils,
  DataSetMetaTypes
}
import com.pacbio.secondary.smrtlink.testkit.MockFileUtils
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetExporter
import JobModels._

trait JobUtils extends SecondaryJobJsonProtocol {

  /**
    * Load a datastore JSON and convert all paths to relative, writing it to a
    * temporary file (or optional output path)
    * @param rootPath   base job directory to which all paths should be relative
    * @param dataStorePath   path to datastore JSON
    * @param dsOutPath  optional path to write to (default: new tmp file)
    */
  protected def relativizeDataStore(rootPath: Path,
                                    dataStorePath: Path,
                                    dsOutPath: Option[Path] = None): Path = {
    val ds = FileUtils
      .readFileToString(dataStorePath.toFile, "UTF-8")
      .parseJson
      .convertTo[PacBioDataStore]
      .relativize(rootPath)
    val dsOut =
      dsOutPath.getOrElse(Files.createTempFile(s"datastore-relpaths", ".json"))
    FileUtils.writeStringToFile(dsOut.toFile, ds.toJson.prettyPrint, "UTF-8")
    dsOut
  }

  /**
    * Load a datastore JSON and convert paths to absolute, writing it to a
    * temporary file (or optional output path)
    * @param rootPath   base job directory to which input paths are relative
    * @param dataStorePath   path to datastore JSON
    * @param dsOutPath  optional path to write to (default: new tmp file)
    */
  protected def absolutizeDataStore(rootPath: Path,
                                    dataStorePath: Path,
                                    dsOutPath: Option[Path] = None): Path = {
    val ds = FileUtils
      .readFileToString(dataStorePath.toFile, "UTF-8")
      .parseJson
      .convertTo[PacBioDataStore]
      .absolutize(rootPath)
    val dsOut =
      dsOutPath.getOrElse(Files.createTempFile(s"datastore-abspaths", ".json"))
    FileUtils.writeStringToFile(dsOut.toFile, ds.toJson.prettyPrint, "UTF-8")
    dsOut
  }

  protected def getDataStore(rootPath: Path): Option[PacBioDataStore] = {
    val p1 = rootPath.resolve("datastore.json")
    val p2 = rootPath.resolve("workflow/datastore.json")
    val dataStorePath = if (p1.toFile.exists) {
      Some(p1)
    } else if (p2.toFile.exists) {
      Some(p2)
    } else {
      None
    }
    dataStorePath.map { p =>
      FileUtils
        .readFileToString(p.toFile, "UTF-8")
        .parseJson
        .convertTo[PacBioDataStore]
        .relativize(rootPath)
    }
  }
}

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
    * @param out  open ZipOutputStream object
    * @param path  directory path to export
    * @param basePath  root path, archive paths will be relative to this
    */
  protected def exportPath(path: Path, basePath: Path): Long = {
    val f = path.toFile
    if (f.isFile) {
      if (FilenameUtils.getName(path.toString) == "datastore.json") {
        val ds = relativizeDataStore(basePath, path)
        exportFile(path, basePath, Some(ds))
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

  private def convertEntryPointPaths(entryPoints: Seq[BoundEntryPoint],
                                     jobPath: Path): Seq[BoundEntryPoint] = {
    entryPoints.map { e =>
      val dsMeta = getDataSetMiniMeta(e.path)
      val ext = dsMeta.metatype.fileType.fileExt
      // this is consistent with how SubreadSet entry points are written
      // in upstream jobs
      val outputPath = Paths.get(s"entry-points/${dsMeta.uuid.toString}.$ext")
      e.copy(path = outputPath)
    }
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
                  logger.warn(s"Skipping duplicate entry ${o.path.toString}");
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
  def toZip(entryPoints: Seq[BoundEntryPoint] = Seq.empty[BoundEntryPoint])
    : Try[JobExportSummary] = {
    val jobPath = Paths.get(job.path)
    if (jobPath.toFile.isFile) {
      throw new RuntimeException(s"${jobPath.toString} is not a directory")
    }
    val entryPointsOut = convertEntryPointPaths(entryPoints, jobPath)
    val datastore = getDataStore(jobPath)
    val manifest = ExportJobManifest(job, entryPointsOut, datastore)
    val manifestFile = Files.createTempFile("export-job-manifest", ".json")
    FileUtils.writeStringToFile(manifestFile.toFile,
                                manifest.toJson.prettyPrint,
                                "UTF-8")
    var nBytes: Long = exportPath(jobPath, jobPath) +
      exportEntryPoints(entryPoints, jobPath) +
      exportFile(jobPath.resolve("export-job-manifest.json"),
                 jobPath,
                 Some(manifestFile))
    out.close
    Try { JobExportSummary(nBytes) }
  }
}

object ExportJob {
  def apply(job: EngineJob,
            zipFileName: Path,
            entryPoints: Seq[BoundEntryPoint] = Seq.empty[BoundEntryPoint]) = {
    new JobExporter(job, zipFileName).toZip(entryPoints)
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
    var zis = new ZipInputStream(new FileInputStream(zipFile.toFile))
    //get the zipped file list entry
    var ze = Option(zis.getNextEntry())
    var nFiles = 0
    while (ze.isDefined) {
      val fileName = ze.get.getName()
      val newFile = jobPath.resolve(fileName).toFile
      logger.debug(s"Deflating ${newFile.getAbsoluteFile}")
      Paths.get(newFile.getParent).toFile.mkdirs
      val fos = new FileOutputStream(newFile)
      var buffer = new Array[Byte](BUFFER_SIZE)
      var len = 0
      while ({ len = zis.read(buffer); len > 0 }) {
        fos.write(buffer, 0, len)
      }
      fos.close()
      if (FilenameUtils.getName(fileName) == "datastore.json") {
        logger.info(s"Updating paths in ${fileName}")
        absolutizeDataStore(jobPath, newFile.toPath, Some(newFile.toPath))
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
    var zf = new ZipFile(zipFile.toFile)
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
          "Can't read export-job-manifest.json in $zipFile.  Only jobs exported through the SMRT Link export-jobs service may be imported.")
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

    def toFx(x: Path, name: String): Path = {
      val p = x.resolve(name)
//      if (!Files.exists(p)) {
//        Files.createFile(p)
//      }
      p
    }

    val toP = toPx(outputDir, _: String)
    val toF = toFx(outputDir, _: String)

    // This is where the datastore.json will be written. Keep this
    val workflowPath = toP("workflow")

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
      toFx(workflowPath, "datastore.json"),
      toFx(workflowPath, "entry-points.json"),
      toFx(workflowPath, "jobOptions-report.json")
    )

    logger.info(s"Successfully created resources")
    r
  }

  /**
    * Get the fundamental "log" for the job
    * FIXME(mpkocher)(2016-12-4) Centralizing this duplication. Should reevaluate the fundamental design
    *
    * @param path  Path to the Log file
    * @return
    */
  def toSmrtLinkJobLog(path: Path,
                       description: Option[String] = None): DataStoreFile = {

    val now = JodaDateTime.now()
    val desc = description.getOrElse(JobConstants.DATASTORE_FILE_MASTER_DESC)

    DataStoreFile(
      UUID.randomUUID(),
      JobConstants.DATASTORE_FILE_MASTER_LOG_ID,
      FileTypes.LOG.fileTypeId,
      // probably wrong; the file isn't closed yet.  But it won't get
      // closed until after this method completes.
      path.toFile.length,
      now,
      now,
      path.toString,
      isChunked = false,
      JobConstants.DATASTORE_FILE_MASTER_NAME,
      desc
    )
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

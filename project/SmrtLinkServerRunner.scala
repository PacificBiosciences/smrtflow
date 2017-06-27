import sbt._
import java.time.LocalDateTime


trait SmrtLinkServerRunner {
  def start(log: Logger): Unit
  def stop(log: Logger): Unit
}

/**
  *
  * @param assemblyJarName Path to the SMRT Link Server Analysis Jar file
  */
class SmrtLinkAnalysisServerRunner(assemblyJarName: File) extends SmrtLinkServerRunner {

  var serverProcess: Option[Process] = None

  val serverArgs = Seq("-jar", assemblyJarName.getAbsolutePath, "--log-level", "DEBUG", "--log-file", "sim-server.log")
  val mainClass = "com.pacbio.secondary.smrtlink.app.SmrtLinkSmrtServer"
  //val forkOptions = ForkOptions(envVars = Map("KEY" -> "value"))
  val forkOptions = ForkOptions()

  override def start(log: Logger) = {
    log.info(s"Assembly Jar name $assemblyJarName")
    log.info(s"Starting SMRT Link Analysis Server ${LocalDateTime.now()} with options:$forkOptions and args:$serverArgs")
    // This needs to be forked in Parallel models
    val p = Fork.java.fork(forkOptions, serverArgs)
    serverProcess = Some(p)
    log.info(s"process exit code='${p.exitValue()}'")
  }

  override def stop(log: Logger) = {
    log.info(s"Stopping SMRT Link Analysis Server ${LocalDateTime.now()}")
    serverProcess.foreach(_.destroy())
  }
}
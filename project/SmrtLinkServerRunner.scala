import sbt._
import java.time.LocalDateTime


trait SmrtLinkServerRunner {
  val log: Logger
  def start(): Unit
  def stop(): Unit
}

/**
  *
  * @param log SBT logger
  * @param assemblyJarName Path to the SMRT Link Server Analysis Jar file
  */
class SmrtLinkAnalysisServerRunner(assemblyJarName: File, override val log: Logger) extends SmrtLinkServerRunner {

  var serverProcess: Option[Process] = None

  val serverArgs = Seq("-jar", assemblyJarName.getAbsolutePath, "--log-level", "DEBUG", "--log-file", "sim-server.log")
  val mainClass = "com.pacbio.secondary.smrtlink.app.SmrtLinkSmrtServer"
  //val forkOptions = ForkOptions(envVars = Map("KEY" -> "value"))
  val forkOptions = ForkOptions()

  override def start():Unit = {
    log.info(s"Assembly Jar name $assemblyJarName")
    log.info(s"Starting SMRT Link Analysis Server at ${LocalDateTime.now()} with options:$forkOptions and args:$serverArgs")
    // This needs to be java.fork so that it does NOT block. The shutdown step will be responsible for shutting down
    // the server
    val p = Fork.java.fork(forkOptions, serverArgs)
    serverProcess = Some(p)
    // Calling p.exitCode will BLOCK
    log.info(s"process '$p'")
  }

  override def stop():Unit = {
    log.info(s"Stopping SMRT Link Analysis Server at ${LocalDateTime.now()}")
    serverProcess.foreach(_.destroy())
  }
}
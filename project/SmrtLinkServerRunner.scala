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

  var serverProcess: Option[scala.sys.process.Process] = None

  val serverArgs = Seq("-jar", assemblyJarName.getAbsolutePath, "--log-level", "INFO", "--log-file", "integration-test-server.log")
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
    log.info(s"Created forked process '$p'")
  }

  override def stop():Unit = {
    log.info(s"Stopping SMRT Link Analysis Server at ${LocalDateTime.now()}")
    serverProcess match {
      case Some(p) =>
        log.info(s"Destroying process $p (isAlive:${p.isAlive()})")
        p.destroy()
      case _ =>
        log.info("No running SMRT Link process to destroy")
    }
  }
}
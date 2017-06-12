import sbt._
import java.time.LocalDateTime


trait SmrtLinkServerRunner {
  def start(): Unit
  def stop(): Unit
}

class SmrtLinkAnalysisServerRunner extends SmrtLinkServerRunner {
  override def start() = {
    println(s"Starting SMRT Link Analysis Server ${LocalDateTime.now()}")
  }

  override def stop() = {
    println(s"Stopping SMRT Link Analysis Server ${LocalDateTime.now()}")
  }
}
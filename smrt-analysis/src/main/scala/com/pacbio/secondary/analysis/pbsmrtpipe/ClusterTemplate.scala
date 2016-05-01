package com.pacbio.secondary.analysis.pbsmrtpipe


import java.io.InputStream
import java.nio.file.Path

import com.typesafe.scalalogging.LazyLogging


case class ClusterTemplate(startTemplate: String, stopTemplate: String) {

  def renderStart(job: CommandTemplateJob) = RenderCommandTemplate.renderCommandTemplate(startTemplate, job)
  def renderStop(job: CommandTemplateJob) = RenderCommandTemplate.renderCommandTemplate(stopTemplate, job)

}

object ClusterTemplate {

  // Required Cluster Command Templates
  val START = "start.tmpl"
  val STOP = "stop.tmpl"


  /**
   * Load a specific cluster template from a file path
   * @param path
   * @return
   */
  private def loadFrom(path: Path): String = {
    scala.io.Source.fromFile(path.toFile).getLines().mkString("\n")
  }

  private def loadFromStream(stream: InputStream): String = {
    scala.io.Source.fromInputStream(stream).getLines().mkString("\n")
  }

  /**
   * Load start.tmpl and stop.tmpl from the Directory
   * @param path
   * @return
   */
  def apply(path: Path): ClusterTemplate = {
    ClusterTemplate(loadFrom(path.resolve(START)), loadFrom(path.resolve(STOP)))
  }


  def loadExampleClusterTemplate: ClusterTemplate = {

    val startStream = getClass.getResourceAsStream("/example-command-cluster_templates/sge_pacbio/start.tmpl")
    val stopStream = getClass.getResourceAsStream("/example-command-cluster_templates/sge_pacbio/stop.tmpl")

    ClusterTemplate(loadFromStream(startStream), loadFromStream(stopStream))
  }


}

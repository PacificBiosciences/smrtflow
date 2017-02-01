package com.pacbio.common.actors.alarms

import com.pacbio.common.actors.PacBioActor
import com.pacbio.common.models.{AlarmMetricUpdate, AlarmMetric}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AlarmActor {
  case object NAME
  case object INIT
  case object UPDATE
}

trait AlarmActor extends PacBioActor {
  import AlarmActor._

  /**
   * This is the name that will be used for Quartz scheduling
   */
  val name: String

  /**
   * Initializes metrics
   */
  def init(): Future[Seq[AlarmMetric]]

  /**
   * Updates metrics
   */
  def update(): Future[Seq[AlarmMetricUpdate]]

  def receive: Receive = {
    case NAME   => respondWith(name)
    case INIT   => pipeWith(init())
    case UPDATE => pipeWith(update())
  }
}

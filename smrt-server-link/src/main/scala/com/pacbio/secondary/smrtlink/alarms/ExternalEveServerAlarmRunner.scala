package com.pacbio.secondary.smrtlink.alarms

import java.net.URL

import akka.actor.ActorSystem
import com.pacbio.common.actors.ActorSystemProvider
import com.pacbio.common.alarms.{AlarmComposer, AlarmRunner}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.{Alarm, AlarmSeverity, AlarmUpdate}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.client.EventServerClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal


/**
  * Created by mkocher on 7/11/17.
  */
class ExternalEveServerAlarmRunner(eveUrl: URL, apiSecret: String)(implicit actorSystem: ActorSystem) extends AlarmRunner{

  val client = new EventServerClient(eveUrl, apiSecret)(actorSystem)

  def getStatus(maxRetries: Int): Future[AlarmUpdate] =
    client.getStatusWithRetry(maxRetries)
        .map(sx => AlarmUpdate(0.0, Some(s"Eve server ${sx.message}"), AlarmSeverity.CLEAR))
      .recover { case NonFatal(ex) => AlarmUpdate(1.0, Some(s"Eve server at $eveUrl is unreachable ${ex.getMessage}"), AlarmSeverity.ERROR)}

  override val alarm = Alarm(
    "smrtlink.alarms.eve_status",
    "Eve Status",
    "Monitor External Eve Service Status")

  override protected def update(): Future[AlarmUpdate] = getStatus(3)
}

trait ExternalEveServerAlarmRunnerProvider {
  this: SmrtLinkConfigProvider with ActorSystemProvider with AlarmComposer =>

  val externalEveServerAlarmRunner: Singleton[ExternalEveServerAlarmRunner] =
    Singleton {() =>
      implicit val system = actorSystem()
      new ExternalEveServerAlarmRunner(externalEveUrl().get, apiSecret())
    }

  addAlarm(externalEveServerAlarmRunner)
}

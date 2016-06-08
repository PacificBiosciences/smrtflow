package com.pacbio.common.scheduling

import java.util.Date

import akka.actor.{Props, ActorRef, ActorSystem}
import com.pacbio.common.actors.{HealthDaoProvider, ActorSystemProvider, HealthDao, PacBioActor}
import com.pacbio.common.dependency.{RequiresInitialization, InitializationComposer, Singleton}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

object HealthActor {
  case object Recalculate
}

class HealthActor(dao: HealthDao) extends PacBioActor {
  import HealthActor._

  def receive: Receive = {
    case Recalculate => pipeWith(dao.recalculate)
  }
}

class HealthMetricRecalculateScheduler(actorSystem: ActorSystem, healthActor: ActorRef) extends RequiresInitialization {
  import HealthActor._

  def init(): Future[Date] = Future {
    QuartzSchedulerExtension(actorSystem).schedule("HealthMetricRecalculate", healthActor, Recalculate)
  }
}

trait HealthMetricRecalculateSchedulerProvider {
  this: ActorSystemProvider with HealthDaoProvider with InitializationComposer =>

  val healthMetricRecalculateScheduler: Singleton[HealthMetricRecalculateScheduler] = requireInitialization(Singleton { () =>
    val actorRef = actorRefFactory().actorOf(Props(classOf[HealthActor], healthDao()))
    new HealthMetricRecalculateScheduler(actorSystem(), actorRef)
  })
}

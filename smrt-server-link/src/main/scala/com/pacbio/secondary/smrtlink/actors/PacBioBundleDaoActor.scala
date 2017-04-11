package com.pacbio.secondary.smrtlink.actors

import java.nio.file.Path

import akka.actor.{Actor, ActorRef, Props}
import com.pacbio.common.actors.ActorRefFactoryProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.PacBioDataBundleIO
import com.pacbio.common.dependency.Singleton
import com.typesafe.scalalogging.LazyLogging

// Central Interface for interacting/mutating the PacBioDataBundleDao
object PacBioBundleDaoActor {
  case object GetAllBundles
  case class GetAllBundlesByType(bundleType: String)
  case class GetAllIOBundlesByType(bundleType: String)
  case class GetBundleByTypeAndVersion(bundleType: String, version: String)
  case class GetBundleIOByTypeAndVersion(bundleType: String, version: String)
  case class GetUpgradableBundle(bundleType: String)
  case class GetActiveIOBundle(bundleType: String)
  case class GetNewestBundle(bundleType: String)
  case class ActivateBundle(bundleType: String, version: String)
  case class AddBundleIO(bundleIO: PacBioDataBundleIO)

}

class PacBioBundleDaoActor(dao: PacBioBundleDao, rootBundleDir: Path) extends Actor with LazyLogging{

  import PacBioBundleDaoActor._

  override def receive = {
    case GetAllBundles =>
      sender ! dao.getBundles
    case GetAllIOBundlesByType(bundleType) =>
      sender ! dao.getBundlesIO(bundleType)
    case GetAllBundlesByType(bundleType) =>
      sender ! dao.getBundlesByType(bundleType)
    case GetBundleIOByTypeAndVersion(bundleType, version) =>
      sender ! dao.getBundleIO(bundleType, version)
    case GetBundleByTypeAndVersion(bundleType, version) =>
      sender ! dao.getBundle(bundleType, version)
    case GetUpgradableBundle(bundleType) =>
      sender ! dao.getBundleUpgrade(bundleType)
    case GetActiveIOBundle(bundleType) =>
      sender ! dao.getActiveBundleIOByType(bundleType)
    case GetNewestBundle(bundleType) =>
      sender ! dao.getNewestBundleVersionByType(bundleType)
    case ActivateBundle(bundleType, version) =>
      sender ! dao.activateBundle(rootBundleDir, bundleType, version)
    case AddBundleIO(b) =>
      sender ! dao.addBundle(b)
  }

}

trait PacBioBundleDaoActorProvider {
  this: ActorRefFactoryProvider with SmrtLinkConfigProvider =>

  val pacBioBundleDaoActor: Singleton[ActorRef] =
    Singleton(() =>  actorRefFactory().actorOf(Props(classOf[PacBioBundleDaoActor], new PacBioBundleDao(pacBioBundles()), pacBioBundleRoot())))
}

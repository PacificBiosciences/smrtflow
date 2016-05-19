package com.pacbio.secondaryinternal.daos

import com.pacbio.common.dependency.Singleton

import com.pacbio.secondaryinternal.JobResolvers
import com.pacbio.secondaryinternal.models.{SmrtLinkJob, SmrtLinkServerResource}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.mutable

//FIXME(mpkocher)(2016-4-18) This should be rethought
trait SmrtLinkResourceDao {

  def getServerById(id: String): Future[SmrtLinkServerResource]
  def getServers: Future[Seq[SmrtLinkServerResource]]
  // return resource or raise
  def addServer(smrtLinkServerResource: SmrtLinkServerResource): Future[SmrtLinkServerResource]

  def getJobById(serverId: String, jobId: Int): Future[SmrtLinkJob]
}

class InMemorySmrtLinkResourceDao(resources: Set[SmrtLinkServerResource]) extends SmrtLinkResourceDao{

  private val smrtLinkServerResources = mutable.Map.empty[String, SmrtLinkServerResource]
  resources.foreach(x => smrtLinkServerResources += (x.id -> x))

  override def getServers = Future {smrtLinkServerResources.values.toList}

  override def getServerById(id: String) = Future {
    smrtLinkServerResources.get(id) match {
      case Some(r) => r
      case _ =>
        //FIXME, raise a proper exception. Need to update to base-smrt-server
        throw new Exception(s"Failed to find SMRT Link Server $id")
    }
  }

  override def addServer(smrtLinkServerResource: SmrtLinkServerResource) = Future {
    // FIXME. This should be clear that it's completely overriding exiting values
    smrtLinkServerResources += (smrtLinkServerResource.id -> smrtLinkServerResource)
    smrtLinkServerResource
  }

  override def getJobById(serverId: String, jobId: Int) = Future {
    smrtLinkServerResources.get(serverId) match {
      case Some(r) => SmrtLinkJob(jobId, "FIXME")
      case _ => throw new Exception(s"Failed to find SMRT Link Server $serverId")
    }
  }


}

trait SmrtLinkResourceDaoProvider {
   val smrtLinkResourceDao: Singleton[SmrtLinkResourceDao]
}

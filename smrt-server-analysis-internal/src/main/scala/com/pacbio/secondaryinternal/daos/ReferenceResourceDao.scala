package com.pacbio.secondaryinternal.daos

import com.pacbio.common.dependency.Singleton

import scala.concurrent.Future
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

import com.pacbio.secondaryinternal.models.ReferenceSetResource


trait ReferenceResourceDao {

  val MAX_RESULTS = 1000

  def getResources(maxResults: Int = MAX_RESULTS): Future[Seq[ReferenceSetResource]]

  def getResourceById(id: String): Future[ReferenceSetResource]

  def addResourceById(referenceResource: ReferenceSetResource): Future[ReferenceSetResource]
}

class InMemoryReferenceResourceDao(resources: mutable.Set[ReferenceSetResource]) extends ReferenceResourceDao {

  // "id" -> ReferenceSet
  private val referenceSets = mutable.Map.empty[String, ReferenceSetResource]
  resources.foreach(x => referenceSets += (x.id -> x))

  override def getResources(maxResults: Int = MAX_RESULTS) = Future {
    referenceSets.values.toList
  }

  override def getResourceById(id: String) = Future {
    referenceSets.get(id) match {
      case Some(r) => r
      case _ => throw new Exception(s"Failed to find ReferenceSet resource with '$id'")
    }
  }

  override def addResourceById(referenceSetResource: ReferenceSetResource) = Future {
    referenceSets += (referenceSetResource.id -> referenceSetResource)
    referenceSetResource
  }
}

trait ReferenceResourceDaoProvider {
  val referenceResourceDao: Singleton[ReferenceResourceDao]
}

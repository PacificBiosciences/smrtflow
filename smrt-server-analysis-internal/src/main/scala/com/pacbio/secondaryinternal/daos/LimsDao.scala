package com.pacbio.secondaryinternal.daos

import com.pacbio.common.dependency.Singleton

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import com.pacbio.secondaryinternal.models.{InternalSubreadSet, RuncodeResource}


trait LimsDao {

  def getSubreadSets(maxResults: Int = 5000): Future[Seq[RuncodeResource]]

  def addSubreadSet(subreadSet: InternalSubreadSet): Future[RuncodeResource]

  // A run code (3150005-0033) is one-to-one mapping of run-code -> SubreadSet
  def getByRunCode(runCode: String): Future[RuncodeResource]

  // An experiment has many runs.  Example 3150005
  def getByExpId(expId: Int): Future[Seq[RuncodeResource]]

}

class InMemoryLimsDao extends LimsDao {

  // Run code should be unique, but it's not clear that this is 100% true
  // runcode -> SubreadSet
  private val subreadSets = mutable.Map.empty[String, RuncodeResource]

  override def getSubreadSets(maxResults: Int = 5000) = Future {
    subreadSets.values.toList
  }

  override def addSubreadSet(internalSubreadSet: InternalSubreadSet) = Future {
    val rcr = internalSubreadSet.asRuncodeResource
    subreadSets += (internalSubreadSet.runcode ->  rcr)
    rcr
  }

  override def getByRunCode(runCode: String) = Future {
    subreadSets.values.find(_.runcode == runCode) match {
      case Some(r) => r
      case _ => throw new Exception(s"Failed to find SubreadSet with $runCode")
    }
  }

  override def getByExpId(expId: Int) = Future {
    // Perhaps this should raise if no values are found
    subreadSets.values.filter(_.expId == expId).toList
  }

}

trait LimsDaoProvider {
  val limsDao: Singleton[LimsDao] = Singleton(() => new InMemoryLimsDao())
}

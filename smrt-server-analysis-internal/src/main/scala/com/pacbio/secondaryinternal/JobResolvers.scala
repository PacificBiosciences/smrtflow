package com.pacbio.secondaryinternal

import java.nio.file.{Paths, Files}
import com.pacbio.secondaryinternal.models.{PortalResolver, JobResource, JobResourceError}

object JobResolvers {

  val idPrefix = "pacbio.secondary_systems."

  def toJobPathString(n: Int) = {

    val ns = n.toString

    // do a zfill-esque operation
    val p = 6 - ns.length match {
      case 0 => ns
      case _ => (0 to 5 - ns.length).foldLeft("")((a, b) => a + "0") + ns
    }

    val prefix = p slice (0, 3)
    s"$prefix/$p"
  }

  def toJobPath(basePath: String, n: Int): String = {
    basePath + toJobPathString(n)
  }

  def isValidPath(path: String): Boolean = Files.exists(Paths.get(path))

  val pathMartin = "/mnt/secondary/Smrtpipe/martin/prod/static/analysisJob/"
  val pathPortalInternal = "/mnt/secondary/iSmrtanalysis/current/common/jobs/"
  val pathPortalBeta = "/mnt/secondary/Smrtanalysis/current/common/jobs/"
  val pathSiv3Portal = "/mnt/secondary-siv/nightlytest/siv3/smrtanalysis/current/common/jobs/"
  val pathSiv4Portal = "/mnt/secondary-siv/nightlytest/siv4/smrtanalysis/current/common/jobs/"

  def toId(s: String) = idPrefix + s.toLowerCase

  def getResolversList = Seq(PortalResolver(toId("martin"), pathMartin),
    PortalResolver(toId("internal"), pathPortalInternal),
    PortalResolver(toId("beta"), pathPortalBeta),
    PortalResolver(toId("siv3"), pathSiv3Portal),
    PortalResolver(toId("siv4"), pathSiv4Portal))

  val resolvers = getResolversList.map(x=> x.systemId -> x).toMap

  def resolver(systemId: String, jobId: Int): Either[JobResourceError, JobResource] = {
    // this is a bit clumsy. Not really sure how proper exception handling model
    if(resolvers.contains(systemId)) {
      val r = resolvers(systemId)
      val p = toJobPath(r.path, jobId)
      if(isValidPath(p)) {
        Right(JobResource(jobId, systemId, p))
      } else {
        Left(JobResourceError(s"Unable to resolve job id $jobId from $systemId"))
      }

    } else {
      Left(JobResourceError(s"Failed to get find job resource $jobId from system '$systemId'"))
    }
  }

}

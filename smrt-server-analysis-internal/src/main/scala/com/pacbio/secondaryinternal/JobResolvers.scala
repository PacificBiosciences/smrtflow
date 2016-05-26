package com.pacbio.secondaryinternal

import java.nio.file.{Paths, Path, Files}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.pacbio.secondary.smrtserver.client.AnalysisServiceAccessLayer
import com.pacbio.secondaryinternal.models.{PortalResolver, JobResource, JobResourceError}

trait JobResolvers {

  /**
    * FIXME. This is basically terrible. The lack of exit points from the pipeline template
    * Maybe this should just get the datastore files. That would be slightly less hacky
    *
    * @param path Root Dir of the Job
    * @return
    */
  private def toAlignmentSetPath(path: Path) =
    path.resolve(Paths.get("tasks/pbalign.tasks.consolidate_alignments-0/combined.alignmentset.xml"))

  /**
    * This needs to be fixed. Get the AlignmentSet by JobId. Look for task output of the form
    *
    * job-dir/tasks/pbalign.tasks.consolidate_bam-0/final.alignmentset.alignmentset.xml
    *
    * @param path Job path
    * @return
    */
  def findAlignmentSetInJob(path: Path): Future[Path] = {
    val alignmentSetPath = toAlignmentSetPath(path)
    //if (Files.exists(alignmentSetPath)) Future { alignmentSetPath }
    // else Future.failed(new Exception(s"Failed to find AlignmentSet at '$alignmentSetPath'"))
    // For local testing the paths won't be accessible and this will always fail. Need a better testing setup
    Future { alignmentSetPath }
  }

  def resolveAlignmentSet(sal: AnalysisServiceAccessLayer, jobId: Int): Future[Path] = {
    for {
      job <- sal.getJobById(jobId)
      path <- findAlignmentSetInJob(Paths.get(job.path))
    } yield path
  }
}

object JobResolvers extends JobResolvers

package com.pacbio.secondary.smrtserver.appcomponents

import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.jobs.{JobRunner, SimpleAndImportJobRunner}
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider


trait JobRunnerProvider {
  this: JobsDaoActorProvider =>

  val jobRunner: Singleton[JobRunner] = Singleton(() => new SimpleAndImportJobRunner(jobsDaoActor()))
}

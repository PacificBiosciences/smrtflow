package com.pacbio.secondary.smrtlink.app

import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.analysis.jobs.{JobRunner, SimpleAndImportJobRunner}
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider


trait JobRunnerProvider {
  this: JobsDaoActorProvider =>

  val jobRunner: Singleton[JobRunner] = Singleton(() => new SimpleAndImportJobRunner(jobsDaoActor()))
}

package com.pacbio.secondary.smrtlink

import java.nio.file.{Files, Paths}

import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.models.DataSetMetaDataSet
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

/**
  * Created by mkocher on 2/15/17.
  */
package object dataintegrity extends LazyLogging{

  trait BaseDataIntegrity {

    /**
      * The Default value for calling the integrity checker
      */
    val defaultInterval: Option[FiniteDuration] = None

    /**
      * Globablly unique identifer for the Runner
      * @return
      */
    val runnerId: String

    /**
      * Main function to execute Health & DataIntegrity checks of the
      * System.
      *
      * TOOD(mpkocher) Any failures should raise the smrtflow.alarms.dataintegrity with FAIL
      *
      * @return
      */
    def run(): Future[MessageResponse]
  }

}

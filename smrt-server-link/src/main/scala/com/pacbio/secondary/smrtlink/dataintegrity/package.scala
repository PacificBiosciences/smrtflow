package com.pacbio.secondary.smrtlink

import java.nio.file.{Files, Paths}

import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.models.DataSetMetaDataSet
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by mkocher on 2/15/17.
  */
package object dataintegrity extends LazyLogging{

  trait BaseDataIntegrity {
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

package com.pacbio.secondary.lims

import com.pacbio.secondary.lims.database.{DefaultDatabase, JdbcDatabase, TestDatabase}
import com.pacbio.secondary.lims.services.{ImportLimsYml, ResolveDataSet}
import com.pacbio.secondary.lims.util.{StressConfig, StressUtil}
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest


/**
 * Performs a stress test of the LIMS import and alias services
 *
 * Spec that verifies importing multiple lims.yml and making multiple aliases. This is also helpful
 * if profiling to see threading, memory, CPU and DB performance.
 */
class StressTestSpec extends Specification
    // Probably should bind a server and make RESTful calls
    with Specs2RouteTest
    // swap the DB here. TestDatabase is in-memory
    //with DefaultDatabase with JdbcDatabase
    with TestDatabase
    // routes that will use the test database
    with ImportLimsYml
    with ResolveDataSet
    // adds the stress testing utilty methods
    with StressUtil {
  //override lazy val jdbcUrl = "jdbc:h2:/tmp/stress_test;CACHE_SIZE=100000" // example file-backed DB override

  def actorRefFactory = system

  createTables

  "Multiple lims.yml files" should {
    "Import and be resolvable in a minimal stress test" in {
      val c = StressConfig(imports = 10, queryReps = 3)
      val sr = stressTest(c)
      sr.noImportFailures() must beTrue
      sr.noLookupFailures() must beTrue
    }
  }
}


package com.pacbio.secondary.lims

import com.pacbio.secondary.lims.database.TestDatabase
import com.pacbio.secondary.lims.services.{ImportLimsYml, ResolveDataSet}
import com.pacbio.secondary.lims.util.{StressConfig, StressResults, StressUtil}
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest



/**
 * Performs a stress test of the LIMS import and alias services
 *
 * WIP: will remove this comment when done.
 *
 * Designed to be helpful for the following use cases.
 *
 * - Spec that verifies importing multiple lims.yml and making multiple aliases
 * - Performance tuning of a disk-backed DB
 *   - avg/min/max time per data creation (aka are INSERTS and indexing fast enough?)
 *   - avg/min/max per query for all queries (aka are SELECTS and JOINS fast enough?)
 * - Comparing different database backends
 */
class StressTestSpec extends Specification
    // Probably should bind a server and make RESTful calls
    with Specs2RouteTest
    // swap the DB here. TestDatabase is in-memory
    with TestDatabase
    // routes that will use the test database
    with ImportLimsYml
    with ResolveDataSet
    // adds the stress testing utilty methods
    with StressUtil {

  def actorRefFactory = system

  "Multiple lims.yml files" should {
    "Import and be resolvable in a minimal stress test" in {
      val sr = stressTest(StressConfig(numLimsYml = 3, numReplicats = 3))
      sr.noImportFailures() must beTrue
      sr.noLookupFailures() must beTrue
    }
  }
}


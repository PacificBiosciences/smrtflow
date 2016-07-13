package com.pacbio.secondary.lims

import com.pacbio.secondary.lims.database.TestDatabase
import com.pacbio.secondary.lims.services.{ImportLimsYml, ResolveDataSet}
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
    "Import and be resolvalbe in a minimal stress test" in {
      val c = StressConfig(numLimsYml = 3, numReplicats = 3)
      val sr = new StressResults(
        for (i <- 1 to c.numLimsYml) yield time(postLimsYml(mockLimsYml(i, s"$i-0001"))),
        for (i <- 1 to c.numLimsYml) yield time(getExperimentOrRunCode(i)),
        for (i <- 1 to c.numLimsYml) yield time(getExperimentOrRunCode(s"$i-0001"))
      )
      sr.noImportFailures() must beTrue
      sr.noLookupFailures() must beTrue
    }
  }
}


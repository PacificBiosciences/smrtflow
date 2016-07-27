package com.pacbio.secondary.lims

import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

import com.pacbio.secondary.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.lims.database.TestDatabase
import com.pacbio.secondary.lims.services.{ImportLims, LookupSubreadset, ResolveDataSet}
import com.pacbio.secondary.lims.util.{StressConfig, StressUtil}
import com.pacificbiosciences.pacbiodatasets.SubreadSet
import org.specs2.mutable.Specification
import org.specs2.specification.{Fragments, Step}
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}


/**
 * Performs a stress test of the LIMS import and alias services
 *
 * Spec that verifies importing multiple lims.yml and making multiple aliases. This is also helpful
 * if profiling to see threading, memory, CPU and DB performance.
 *
 * Increase the `StressConfig` params for a better profiling example. 100,000 entries at 3x replicate
 * queries should take on the order of 25s on a Mac laptop  using a disk-backed H2 JDBC URL.
 */
class StressTestSpec extends Specification
    // Probably should bind a server and make RESTful calls
    with Specs2RouteTest
    // swap the DB here. TestDatabase is in-memory
    //with DefaultDatabase with JdbcDatabase
    with TestDatabase
    // routes that will use the test database
    with ImportLims
    with LookupSubreadset
    with ResolveDataSet
    // adds the stress testing utilty methods
    with StressUtil {
  //override lazy val jdbcUrl = "jdbc:h2:/tmp/stress_test;CACHE_SIZE=100000" // example file-backed DB override

  // TODO: can remove this when specs2 API is upgraded
  override def map(fragments: =>Fragments) = Step(beforeAll) ^ fragments

  override def subreadset(path: Path, context: String): Option[SubreadSet] = {
    Try(DataSetLoader.loadSubreadSet(new ByteArrayInputStream(mockSubreadset(UUID.randomUUID()).getBytes()))) match {
      case Success(ds) => Some(ds)
      case Failure(t) => None
    }
  }

  def beforeAll = createTables()
  def actorRefFactory = system

  private implicit val timeout = RouteTestTimeout(new FiniteDuration(10, TimeUnit.SECONDS))

  "Multiple lims.yml files" should {
    "Import and be resolvable in a minimal stress test" in {
      val c = StressConfig(imports = 10, queryReps = 3)
      val sr = stressTest(c)
      sr.noImportFailures() must beTrue
      sr.noLookupFailures() must beTrue
    }
  }
}


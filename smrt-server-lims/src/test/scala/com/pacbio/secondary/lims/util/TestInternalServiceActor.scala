package com.pacbio.secondary.lims.util

import java.util.UUID

import com.pacbio.secondary.lims.InternalServiceActor
import com.typesafe.config.ConfigFactory

/**
 * For a test server to check HTTP-based use of RESTful services and CLI.
 */
class TestInternalServiceActor
  extends InternalServiceActor
  with CommandLineToolsConfig {

  override lazy val jdbcUrl = s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=30"

  override lazy val conf = ConfigFactory.parseString(
    s"""pb-services {
        |  db-uri = "$jdbcUrl"
        |  host = "$testHost"
        |  port = $testPort
        |}""".stripMargin)

  // need to make the tables before test run or else slower CI such as CircleCI timeout
  createTables()
}

trait CommandLineToolsConfig {
  val testHost = "127.0.0.1"
  val testPort = 8081
}

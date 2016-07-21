package com.pacbio.secondary.lims.util

import java.util.UUID

/**
 * Makes fake UUIDs so that the file-based dependency in prod need not be present in every test case
 */
trait TestLookupSubreadsetUuid {
  def lookupUuid(path: String): Option[String] = {
    Some(UUID.randomUUID().toString)
  }
}
package com.pacbio.secondary.lims.util

import com.pacificbiosciences.pacbiodatasets.SubreadSet

/**
 * Makes fake UUIDs so that the file-based dependency in prod need not be present in every test case
 */
trait TestLookupSubreadset {
  def subreadset(path: String): Option[SubreadSet] = {
    None
  }
}
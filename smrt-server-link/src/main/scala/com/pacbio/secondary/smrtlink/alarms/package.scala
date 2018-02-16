package com.pacbio.secondary.smrtlink

package object alarms {

  // Centralizing these here so they can be referred to in other locations in the code base
  object AlarmTypeIds {
    val SERVER_CHEM = "smrtlink.alarms.chemistry_update_server"
    val SERVER_EVE = "smrtlink.alarms.eve_status"
    val DIR_TMP = "smrtlink.alarms.tmp_dir"
    val DIR_JOB = "smrtlink.alarms.job_dir"
    val CLEANUP_TMP_DIR = "smrtlink.alarms.tmp_dir_cleanup"
  }

}

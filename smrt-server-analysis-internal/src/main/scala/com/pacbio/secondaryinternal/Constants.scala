package com.pacbio.secondaryinternal

import com.pacbio.secondaryinternal.models.SmrtLinkServerResource

/**
  * Created by mkocher on 12/19/15.
  */
object Constants {

  final val PBI_COLLECTIONS_ROOT = "/pbi/dept/secondary/siv/smrtlink"

  final val SL_BETA = SmrtLinkServerResource("smrtlink-beta", "SMRT Link Beta", "http://smrtlink-beta", 8081, PBI_COLLECTIONS_ROOT + "/smrtlink-beta/smrtsuite/userdata/jobs_root/")
  final val SL_NIGHTLY = SmrtLinkServerResource("smrtlink-nightly", "SMRT Link Nightly", "http://smrtlink-nightly", 8081, PBI_COLLECTIONS_ROOT + "/smrtlink-nightly/smrtsuite/userdata/jobs_root/")
  final val SL_ALPHA = SmrtLinkServerResource("smrtlink-alpha", "SMRT Link Alpha", "http://smrtlink-alpha", 8081, PBI_COLLECTIONS_ROOT + "/smrtlink-alpha/smrtsuite/userdata/jobs_root/")
  final val SL_BIHOURLY = SmrtLinkServerResource("smrtlink-bihourly", "SMRT Link BiHourly", "http://smrtlink-bihourly", 8081, PBI_COLLECTIONS_ROOT + "/smrtlink-bihourly/smrtsuite/userdata/jobs_root/")

  final val SL_SYSTEMS = Set(SL_BETA, SL_NIGHTLY, SL_ALPHA, SL_BIHOURLY)
}

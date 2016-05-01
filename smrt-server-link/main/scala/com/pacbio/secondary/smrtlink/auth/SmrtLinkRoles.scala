package com.pacbio.secondary.smrtlink.auth

import com.pacbio.common.auth._

object SmrtLinkRoles {
  case object RUN_DESIGN_WRITE extends AbstractRole()
  case object REGISTRY_WRITE extends AbstractRole()
}

trait SmrtLinkRolesInit {
  import SmrtLinkRoles._

  RUN_DESIGN_WRITE.register()
  REGISTRY_WRITE.register()
}

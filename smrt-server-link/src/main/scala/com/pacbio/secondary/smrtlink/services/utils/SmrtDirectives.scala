package com.pacbio.secondary.smrtlink.services.utils

import akka.http.scaladsl.server.Directive
import com.pacbio.secondary.smrtlink.models.UserRecord

object SmrtDirectives {

  def extractOptionalUserRecord: Directive[Tuple1[Option[UserRecord]]] =
    Directive[Tuple1[Option[UserRecord]]] { inner => ctx =>
      inner(None)(ctx)
    }

  def extractRequiredUserRecord: Directive[Tuple1[UserRecord]] =
    Directive[Tuple1[UserRecord]] { inner => ctx =>
      inner(UserRecord("stuff", Some("more-stuff")))(ctx)
    }

}

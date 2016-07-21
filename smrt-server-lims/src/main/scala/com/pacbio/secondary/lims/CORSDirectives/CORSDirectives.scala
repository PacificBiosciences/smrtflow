package com.pacbio.secondary.lims.CORSDirectives

import spray.http.HttpHeaders.RawHeader
import spray.routing._
import spray.http._
import spray.http.StatusCodes.Forbidden

// inspired by https://gist.github.com/ayosec/4324747
trait CORSDirectives { this: HttpService =>
  def respondWithCORSHeaders() =
    respondWithHeaders(
      RawHeader("Access-Control-Allow-Origin", "*"),
      RawHeader("Access-Control-Allow-Credentials", "true"))
}
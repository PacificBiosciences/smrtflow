package com.pacbio.secondary.smrtlink.auth.hmac

import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server._

import scala.concurrent.Future

case class HmacException(msg: String)

case class HmacConfiguration(pattern: String, header: String = "Authenticate") {
  val regex = pattern.r
}

object HmacException {
  val InvalidFormat = HmacException("Invalid hmac format")
}

trait HmacConfig {
  val pattern = """^hmac (\S+):(\S+)$"""
  val header = "Authentication"
  val regex = pattern.r
}

trait Directives { this: HmacConfig =>
  def parseHeader(header: String): Either[HmacData, HmacException] =
    header match {
      case regex(uuid, hash) => Left(HmacData(uuid, hash))
      case _ => Right(HmacException.InvalidFormat)
    }

  def validateHmacKey(c: HmacConfiguration): Directive0 = Directive[Unit] {
    inner => ctx =>
      inner(Unit)(ctx)
  }

//  def authenticate[A](block: A => Route)(
//      implicit auth: Authentication[A]): Route = requestInstance { request =>
//    request.headers
//      .find(_.name == header)
//      .map(
//        h =>
//          parseHeader(h.value).fold(
//            verify(_, s"${request.method}+${request.uri.path}", block),
//            ex => invalidFormat(ex.msg)))
//      .getOrElse(missingHeader)
//  }

//  def verify[A](hmacData: HmacData, uri: String, block: A => Route)(
//      implicit auth: Authentication[A]): Route = {
//    auth
//      .authenticate(hmacData, uri)
//      .map(account => block(account))
//      .getOrElse(invalidCredentials)
//  }
//
//  def missingHeader = reject(MissingHeaderRejection(header))
//  def invalidFormat(msg: String) =
//    reject(MalformedHeaderRejection(header, msg))
//  def invalidCredentials =
//    reject(AuthenticationFailedRejection(CredentialsRejected, Nil))
}

object Directives extends Directives with HmacConfig

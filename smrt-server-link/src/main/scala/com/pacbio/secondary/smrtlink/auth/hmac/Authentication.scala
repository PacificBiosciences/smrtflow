package com.pacbio.secondary.smrtlink.auth.hmac

case class HmacData(uuid: String, hash: String)

trait Authentication[A] { this: Signer =>
  def authenticate(hmac: HmacData, uri: String): Option[A] = {
    val (account, secret) = accountAndSecret(hmac.uuid)
    for (a <- account; s <- secret if valid(hmac.hash, s, uri)) yield a
  }

  def accountAndSecret(uuid: String): (Option[A], Option[String])
}

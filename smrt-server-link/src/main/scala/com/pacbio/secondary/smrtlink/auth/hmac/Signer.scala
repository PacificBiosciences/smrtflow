package com.pacbio.secondary.smrtlink.auth.hmac

import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import javax.xml.bind.DatatypeConverter

trait Signer {
  def valid(hash: String, secret: String, uri: String): Boolean
  def generate(secret: String, uri: String, timestamp: Long): String
}

trait SignerConfig {
  val algorithm = "HmacSHA256"
  val separator = "+"
  val tolerance = 5
}

trait DefaultSigner extends Signer { this: SignerConfig =>
  def valid(hash: String, secret: String, uri: String): Boolean = {
    val ts = timestamp
    if (generate(secret, uri, ts) == hash) true
    else generate(secret, uri, previousTimestamp(ts)) == hash
  }

  def generate(secret: String, uri: String, timestamp: Long): String = {
    val mac = Mac.getInstance(algorithm)
    mac.init(new SecretKeySpec(secret.getBytes, algorithm))
    DatatypeConverter.printBase64Binary(
      mac.doFinal((uri + separator + timestamp).getBytes))
  }

  def timestamp = System.currentTimeMillis / Math.pow(10, tolerance).toLong
  def previousTimestamp(timestamp: Long) = timestamp - 1
}

object Signer extends DefaultSigner with SignerConfig

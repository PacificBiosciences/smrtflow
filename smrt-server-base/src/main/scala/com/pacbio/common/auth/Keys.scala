package com.pacbio.common.auth

import java.security.KeyFactory
import java.security.spec.{X509EncodedKeySpec, PKCS8EncodedKeySpec}

import com.google.common.io.ByteStreams

// TODO(smcclellan): Add unit tests

/**
 * Contains the private and public key to be used for encoding authentication tokens. The private key needs to be kept
 * secure, and never stored anywhere except for PacBio servers.
 *
 * To generate new key files, use the following commands:
 * {{{
 * > openssl genrsa -out privkey.pem 2048
 * > openssl rsa -in privkey.pem -pubout -outform DER -out pacbio_public_key.der
 * > openssl pkcs8 -topk8 -inform PEM -outform DER -in privkey.pem -out pacbio_private_key.der -nocrypt
 * }}}
 */
object Keys {
  val PRIVATE_KEY_RESOURCE = "/pacbio_private_key.der"
  val PUBLIC_KEY_RESOURCE = "/pacbio_public_key.der"

  val PRIVATE_KEY = {
    val keyStream = getClass.getResourceAsStream(PRIVATE_KEY_RESOURCE)
    val keyBytes = ByteStreams.toByteArray(keyStream)

    keyStream.close()

    val spec = new PKCS8EncodedKeySpec(keyBytes)
    val factory = KeyFactory.getInstance("RSA")
    factory.generatePrivate(spec)
  }

  val PUBLIC_KEY = {
    val keyStream = getClass.getResourceAsStream(PUBLIC_KEY_RESOURCE)
    val keyBytes = ByteStreams.toByteArray(keyStream)

    keyStream.close()

    val spec = new X509EncodedKeySpec(keyBytes)
    val factory = KeyFactory.getInstance("RSA")
    factory.generatePublic(spec)
  }
}

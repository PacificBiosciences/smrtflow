package com.pacbio.secondary.smrtlink.client

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl._
import java.net.URL

import scala.concurrent.Future
import scala.util.control.NoStackTrace

import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.Http.OutgoingConnection
import akka.stream.scaladsl.Flow
import com.typesafe.sslconfig.akka.AkkaSSLConfig

class AuthenticationError(msg: String)
    extends RuntimeException(msg)
    with NoStackTrace

trait SecureClientBase {

  implicit val actorSystem: ActorSystem

  private val trustfulSslContext = {
    // Create a trust manager that does not validate certificate chains.
    val permissiveTrustManager: TrustManager = new X509TrustManager() {
      override def checkClientTrusted(chain: Array[X509Certificate],
                                      authType: String): Unit = {}
      override def checkServerTrusted(chain: Array[X509Certificate],
                                      authType: String): Unit = {}
      override def getAcceptedIssuers(): Array[X509Certificate] = {
        null
      }
    }

    val initTrustManagers = Array(permissiveTrustManager)
    val ctx = SSLContext.getInstance("TLS")
    ctx.init(null, initTrustManagers, new SecureRandom())
    ctx
  }

  private val badSslConfig =
    AkkaSSLConfig()
      .mapSettings(
        s =>
          s.withLoose(
            s.loose
              .withDisableSNI(true)
              .withAcceptAnyCertificate(true)
              .withDisableHostnameVerification(true)))

//  val badCtx: HttpsConnectionContext = ConnectionContext.https(sslContext)
  private val badCtx = Http().createClientHttpsContext(badSslConfig)

  private val permissiveCtx = new HttpsConnectionContext(
    trustfulSslContext,
    badCtx.sslConfig,
    badCtx.enabledCipherSuites,
    badCtx.enabledProtocols,
    badCtx.clientAuth,
    badCtx.sslParameters
  )

  protected def getHttpsConnection(
      host: String,
      port: Int): Flow[HttpRequest, HttpResponse, Future[OutgoingConnection]] =
    Http(actorSystem).outgoingConnectionHttps(
      host,
      port = port,
      connectionContext = permissiveCtx)
}

package com.pacbio.simulator.steps

import java.net.URL

import com.pacbio.simulator.Scenario
import com.pacbio.simulator.StepResult._

import scala.collection.mutable
import scala.concurrent.Future
import scalaj.http.{Http, HttpOptions}

trait HttpSteps {
  this: Scenario with VarSteps =>

  val defaultHost: String = null
  val defaultPort: Int = 8080
  val defaultTimeout: Int = 10000

  object HttpStep {
    def get(path: Var[String]): HttpStep = HttpStep("GET", path)
    def post(path: Var[String], data: Var[String]): HttpStep = HttpStep("POST", path, Some(data))
    def put(path: Var[String], data: Var[String]): HttpStep = HttpStep("PUT", path, Some(data))
    def delete(path: Var[String]): HttpStep = HttpStep("DELETE", path)
  }

  case class HttpStep(meth: String,
                      path: Var[String],
                      data: Option[Var[String]] = None) extends VarStep[String] {

    override val name = s"Http-$meth"

    val headers: mutable.Map[String, String] = new mutable.HashMap
    def header(key: String, value: String): HttpStep = {
      headers(key) = value
      this
    }

    var host: String = defaultHost
    def host(host: String): HttpStep = {
      this.host = host
      this
    }

    var port: Int = defaultPort
    def port(port: Int): HttpStep = {
      this.port = port
      this
    }

    var timeout: Int = defaultTimeout
    def timeout(timeout: Int): HttpStep = {
      this.timeout = timeout
      this
    }

    override def run: Future[Result] = Future {
      val url = new URL("http", host, port, path.get)
      var req = Http(url.toString).method(meth)

      headers.foreach { h => req = req.header(h._1, h._2) }
      data.foreach { d => req = req.postData(d.get) }

      val resp = req
        .option(HttpOptions.readTimeout(timeout))
        .execute[String]()

      output(resp.body)
      if (resp.isSuccess) SUCCEEDED else FAILED(s"Server returned a ${resp.code} response")
    }
  }
}

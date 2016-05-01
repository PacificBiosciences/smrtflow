package com.pacbio.common

import akka.actor.{Status, Actor}

import scala.util.Try
import scala.util.control.NonFatal

package object actors {
  trait PacBioActor extends Actor {
    def respondWith(x: => Any): Unit = {
      sender ! Try(x).recover{case NonFatal(e) => Status.Failure(e)}.get
    }
  }
}

package com.pacbio.secondary.smrtlink

import akka.actor.{Actor, Status}
import akka.pattern.pipe

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

package object actors {
  trait PacBioActor extends Actor {
    def respondWith(x: => Any): Unit = {
      sender ! Try(x).recover{ case NonFatal(e) => Status.Failure(e) }.get
    }

    def pipeWith(x: => Future[Any])(implicit ec: ExecutionContext): Unit = {
      pipe(x.recover{ case NonFatal(e) => Status.Failure(e) }) to sender
    }
  }
}

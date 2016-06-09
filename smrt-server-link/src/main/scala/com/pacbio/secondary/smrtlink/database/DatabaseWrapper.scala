package com.pacbio.secondary.smrtlink.database

import slick.dbio.{DBIOAction, NoStream}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

import scala.concurrent.ExecutionContext.Implicits.global

// TODO: refactor this to be a `run()` method on Dal that the code uses. Right now the code assumes
//       that `Dal.db` exists and uses it. There will be a lot of little changes after this refactor.
class DatabaseWrapper(dal: Dal) {
  def run[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = {
    Future[R] {
      try {
        dal.shareConnection = true
        val f = dal.realDb.run(a)
        Await.result(f, Duration.Inf)
      }
      finally {
        dal.shareConnection = false
        val conn = dal.connectionPool.cachedConnection
        if (conn != null && !conn.isClosed) {
          conn.commit()
          conn.close()
        }
      }
    }
  }
}
package com.pacbio.database

/**
 * Created by jfalkner on 6/10/16.
 */
trait DatabaseListener {
  // life cycle of the nested Future. useful for debugging nested db.run use
  def create(code: String, stacktrace: Throwable): Unit
  def start(code: String, stacktrace: Throwable, queryCount: Int): Unit
  def end(code: String, stacktrace: Throwable, queryCount: Int): Unit
  // summary and query status
  def timeout(code: String, stacktrace: Throwable, t: Throwable): Unit
  def error(code: String, stacktrace: Throwable, t: Throwable): Unit
  def success(code: String, stacktrace: Throwable, result: Any): Unit
  def dbDone(start: Long, end: Long, code: String, stacktrace: Throwable): Unit
  def allDone(start: Long, end: Long, code: String, stacktrace: Throwable): Unit
}

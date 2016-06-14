package com.pacbio.database

/**
 * Created by jfalkner on 6/10/16.
 */
trait DatabaseListener {
  def error(code: String, stacktrace: Throwable, t: Throwable): Unit
  def success(code: String, stacktrace: Throwable, result: Any): Unit
  def dbDone(start: Long, end: Long, code: String, stacktrace: Throwable): Unit
  def allDone(start: Long, end: Long, code: String, stacktrace: Throwable): Unit
}

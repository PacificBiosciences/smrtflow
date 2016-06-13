package com.pacbio.database

/**
 * Created by jfalkner on 6/10/16.
 */
class ProfilingListener extends DatabaseListener {

  var errors = Map[String, Int]()
  var complete = Map[String, Int]()


  override def error(
      code: String,
      stacktrace: Throwable,
      t: Throwable): Unit = {
    errors += (code -> (errors.getOrElse(code, 0) + 1))
    printSummary
  }

  override def allDone(
      start: Long,
      end: Long,
      code: String,
      stacktrace: Throwable): Unit =
    complete += (code -> (complete.getOrElse(code, 0) + 1))
    printSummary

  override def success(
      code: String,
      stacktrace: Throwable,
      result: Any): Unit = Unit

  override def dbDone(
      start: Long,
      end: Long,
      code: String,
      stacktrace: Throwable): Unit = Unit

  def printSummary: Unit = {
    println("*** DB Use Summary ***")
    println("Code,Success, Failures")
    val rows = for(k <- List(errors.keySet ++ complete.keySet).flatten)
      yield (k, complete.getOrElse(k, 0), errors.getOrElse(k, 0))
    for ((k, _, _) <- rows.sortWith(_._2 > _._2))
      println(s"$k, ${complete.getOrElse(k, 0)}, ${errors.getOrElse(k, 0)}")
  }

}

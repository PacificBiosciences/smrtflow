package com.pacbio.database

import java.io._

import scala.collection.mutable.ArrayBuffer

/**
 * Collects and displays Slick Database use frequency and timing
 *
 * This listener takes com.pacbio.database.DatabaseListener events and uses them to track what line
 * of scala code runs DB queries, how often and how long the queries take.
 *
 * See README.md for enabling this code and example output
 *
 *   https://github.com/PacificBiosciences/smrtflow/tree/master/smrt-server-database#debugging-and-profiling
 */
class ProfilingListener extends DatabaseListener {

  var errors = Map[String, Int]()
  var allDoneLog = Map[String, ArrayBuffer[Long]]()
  var dbDoneLog = Map[String, ArrayBuffer[Long]]()

  // life cycle of the nested Future. useful for debugging nested db.run use
  override def create(code: String, stacktrace: Throwable): Unit = {}
  override def start(code: String, stacktrace: Throwable, qc: Int): Unit = {}
  override def end(code: String, stacktrace: Throwable, qc: Int): Unit = {}

  override def timeout(
      code: String,
      stacktrace: Throwable,
      t: Throwable): Unit =
    errors += (code -> (errors.getOrElse(code, 0) + 1))

  override def error(
      code: String,
      stacktrace: Throwable,
      t: Throwable): Unit =
    errors += (code -> (errors.getOrElse(code, 0) + 1))

  override def allDone(
      start: Long,
      end: Long,
      code: String,
      stacktrace: Throwable): Unit =
    allDoneLog += (code -> (allDoneLog.getOrElse(code, ArrayBuffer.empty[Long]) += (end - start)))

  override def success(
      code: String,
      stacktrace: Throwable,
      result: Any): Unit = Unit

  override def dbDone(
      start: Long,
      end: Long,
      code: String,
      stacktrace: Throwable): Unit =
    dbDoneLog += (code -> (dbDoneLog.getOrElse(code, ArrayBuffer.empty[Long]) += (end - start)))

  var latestBuffer = new StringWriter()
  sys addShutdownHook{
    // a dump to the logs
    printSummary(new OutputStreamWriter(System.out))
    // write out to some temp files
    if (new File("/tmp").exists) {
      // write out usage
      val usage = new FileWriter("/tmp/PACBIO_DATABASE_usage.csv")
      try showUsage(usage) finally usage.close()
      // write out timing
      val timing = new FileWriter("/tmp/PACBIO_DATABASE_timing.csv")
      try showTiming(timing) finally timing.close()
    }
  }

  // prints out the frequency of usage along with success and failure
  // make a final row for all keys observed in errors and complete
  def showUsage(buf : Writer = new StringWriter()): Unit = {
    buf.append("Code,Success,Failures\n")
    val rows = for (k <- List(errors.keySet ++ allDoneLog.keySet).flatten)
      yield (k, allDoneLog.getOrElse(k, ArrayBuffer.empty[Long]), errors.getOrElse(k, 0))
    // count entries in the lists
    val sumRows = rows.map(x => (x._1, x._2.length, x._3))
    // sort by most frequent and display
    for ((k, success, fail) <- sumRows.sortWith(_._2 > _._2))
      buf.append(s"$k, $success, $fail\n")
    buf.flush()
  }

  // prints out total use and time spent by the queries
  def showTiming(buf : Writer = new StringWriter()): Unit = {
    buf.append("Code,Sum,Avg,Min,Max,Usage\n")
    val rows = for (k <- dbDoneLog.keySet)
      yield (
          k,
          dbDoneLog.getOrElse(k, ArrayBuffer.empty[Long]).sum,
          dbDoneLog.getOrElse(k, ArrayBuffer.empty[Long]).sum)
    def convert(k: String): (String, Long, Long, Long, Long, Int) = {
      val times = dbDoneLog(k)
      val sum = times.sum
      val avg = sum / times.size
      val min = times.reduceLeft(_ min _)
      val max = times.reduceLeft(_ max _)
      (k, sum, avg, min, max, times.size)
    }
    val rows2: Seq[(String, Long, Long, Long, Long, Int)] = List(dbDoneLog.keySet).flatten.map(k => convert(k))
    // sort by most frequent and display
    for ((k, sum, avg, min, max, freq) <- rows2.sortWith(_._2 > _._2))
      buf.append(s"$k, $sum, $avg, $min, $max, $freq\n")
    buf.flush()
  }

  def printSummary(buf : Writer = new StringWriter()): Unit = {
    buf.append("*** DB Use Summary ***\n")
    showUsage(buf)
    showTiming(buf)
    buf.flush()
  }

}

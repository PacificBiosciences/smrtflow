package com.pacbio.secondary.lims.util

import java.io.{ByteArrayOutputStream, PrintStream}

/**
 * Helper methods to write tests that need to confirm expected stdout and stderr CLI output
 */
trait CommandLineUtil {
  // util method to buffer CLI stderr and provide a string for testing
  def stderr(f: => Unit): String = {
    val err = System.err
    try {
      val buf = new ByteArrayOutputStream()
      System.setErr(new PrintStream(buf))
      f
      buf.toString()
    }
    finally {
      System.setErr(err)
    }
  }

  // util method to buffer CLI stdout and provide a string for testing
  def stdout(f: => Unit): String = {
    val out = System.out
    try {
      val buf = new ByteArrayOutputStream()
      System.setOut(new PrintStream(buf))
      f
      buf.toString()
    }
    finally {
      System.setOut(out)
    }
  }
}

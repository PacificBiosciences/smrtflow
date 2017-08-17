package com.pacbio.secondary.smrtlink.logging

import org.slf4j.MDC
import resource._

/**
 * Used to add mapped diagnostic context that can be closed via automatic resource management.
 *
 * For example:
 *
 * {{{
 * import resource._
 *
 * class FooBar extends ContextualLogging {
 *   def foo() = {
 *     for (_ <- logContext("method", "foo")) {
 *       doFoo()
 *     }
 *   }
 *
 *   def bar() = {
 *     for (_ <- logContext("method" -> "bar", "bar" -> "true")) {
 *       doBar()
 *     }
 *   }
 * }
 * }}}
 *
 * This will ensure that all logging within the {{{doFoo()}}} and {{{doBar()}}} methods will have
 * the appropriate context, and that the context will be cleared after those methods terminate.
 */
trait ContextualLogging {
  def logContext(key: String, value: String): ManagedResource[MDCResource] =
    managed(new MDCResource(key -> value))

  def logContext(pairs: (String, String)*): ManagedResource[MDCResource] =
    managed(new MDCResource(pairs: _*))

  class MDCResource(pairs: (String, String)*) {
    def open(): Unit = pairs.foreach(p => MDC.put(p._1, p._2))
    def close(): Unit = pairs.reverse.foreach(p => MDC.remove(p._1))
  }

  implicit object MDCResource extends Resource[MDCResource] {
    override def open(r: MDCResource) = r.open()
    override def close(r: MDCResource) = r.close()
  }
}

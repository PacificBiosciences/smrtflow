package com.pacbio.common.dependency

import scala.collection.mutable

// scalastyle:off
/**
 * Allows providers to construct a set with elements from multiple providers. To provide a {{{Set[T]}}}, create an
 * object that extends {{{SetBinding[T]}}}, and require a self-type of {{{SetBindings}}} in any provider that needs
 * access to that set. Providers that wish to contribute to the set, can do so with {{{bindToSet}}} method in
 * {{{Singleton}}}. {{{SetBindings}}} must then be mixed in to the complete providers.
 *
 * For example:
 *
 * {{{
 *   import com.pacbio.common.dependency._
 *
 *   // Foo Listeners
 *
 *   trait FooListener {
 *     def foo(): Unit
 *   }
 *
 *   class FooListenerA extends FooListener {
 *     override def foo() = { println("A") }
 *   }
 *
 *   class FooListenerB extends FooListener {
 *     override def foo() = { println("B") }
 *   }
 *
 *   // Foo Listener Providers
 *
 *   object FooListeners extends SetBinding[FooListener]
 *
 *   trait FooListenerAProvider {
 *     final val fooListenerA: Singleton[FooListenerA] = Singleton(() => new FooListenerA).bindToSet(FooListeners)
 *   }
 *
 *   trait FooListenerBProvider {
 *     final val fooListenerB: Singleton[FooListenerA] = Singleton(() => new FooListenerB).bindToSet(FooListeners)
 *   }
 *
 *   // Foo Broadcaster
 *
 *   class FooBroadcaster(listeners: Set[FooListener] {
 *     def broadcastFoo(): Unit = { listeners.foreach(l => l.foo()) }
 *   }
 *
 *   // Foo Broadcaster Provider
 *
 *   trait FooBroadcasterProvider {
 *     this: SetBindings =>
 *
 *     final val fooBroadcaster: Singleton[FooBroadcaster] = Singleton(() => new FooBroadcaster(set(FooListeners)))
 *   }
 *
 *   // App
 *
 *   trait CompleteProvider extends
 *     SetBindings with
 *     FooBroadcasterProvider with
 *     FooListenerAProvider with
 *     FooListenerBProvider
 *
 *   object CompleteProvider extends CompleteProvider
 *
 *   // Should print "A" and "B" on separate lines in an unspecified order.
 *   object FooApp extends App {
 *     CompleteProvider.fooBroadcaster().broadcastFoo()
 *   }
 * }}}
 *
 * The advantage of this approach is that it does not require a central registry of FooListeners. Any part of the
 * application can declare itself a FooListener and be automatically registered by its provider.
 */
trait SetBinding[T] {
  // scalastyle: on
  final private[dependency] val bindings: mutable.HashSet[Singleton[_ <: T]] = new mutable.HashSet[Singleton[_ <: T]]()

  final private[dependency] def registerBinding(obj: Singleton[_ <: T]): Unit = bindings.add(obj)

  final private[dependency] def getAllBindings: Set[T] = bindings.map(_()).toSet
}

/**
 * Trait that can be mixed in to providers that require sets to be constructed from multiple providers.
 * See {{{SetBinding}}}.
 */
trait SetBindings {
  /**
   * Returns the set of objects that have been bound to the given {{{SetBinding}}}. This should only be called after the
   * full set of providers, with all dependencies, has been constructed, or inside the construction of a
   * {{{Singleton}}}.
   */
  final def set[T](bind: SetBinding[T]): Set[T] = bind.getAllBindings
}
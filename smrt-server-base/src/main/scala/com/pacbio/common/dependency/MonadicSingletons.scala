package com.pacbio.common.dependency

import scala.language.implicitConversions

/**
 * EXPERIMENTAL
 *
 * Allows Singletons to be used as monads in for-yield comprehensions. As in:
 *
 * {{{
 *   trait FooBarProvider {
 *     import com.pacbio.common.dependency.MonadicSingletons._
 *
 *     val foo: Singleton[Foo] = Singleton(() => constructFoo())
 *     val bar: Singleton[Bar] = Singleton(() => constructBar())
 *
 *     val foobar: Singleton[FooBar] = for {
 *       f <- foo
 *       b <- bar
 *       if b.isValid
 *     } yield new FooBar(f, b)
 *   }
 * }}}
 *
 * Note that there is one major barrier to implementing this in production. Namely that scala implicitly converts
 * for-yield comprehensions to a chain of method calls. For instance, the above code would be converted to the
 * following:
 *
 * {{{
 *   trait FooBarProvider {
 *     import com.pacbio.common.dependency.MonadicSingletons._
 *
 *     val foo: Singleton[Foo] = Singleton(() => constructFoo())
 *     val bar: Singleton[Bar] = Singleton(() => constructBar())
 *
 *     val foobar: Singleton[FooBar] = foo.flatMap(f => bar.withFilter(b => b.isValid).map(b => new FooBar(f, b)))
 *   }
 * }}}
 *
 * This works fine if the input Singletons are defined locally, but when inputs come from a self-type dependency, a
 * NullPointerException can occur:
 *
 * {{{
 *   trait FooProvider {
 *     val foo: Singleton[Foo] = Singleton(() => constructFoo())
 *   }
 *
 *   trait FooBarProvider {
 *     this: FooProvider =>
 *
 *     import com.pacbio.common.dependency.MonadicSingletons._
 *
 *     val bar: Singleton[Bar] = Singleton(() => constructBar())
 *
 *     // The val "foo" is undefined here, so this call to foo.flatMap raises a NullPointerException
 *     val foobar: Singleton[FooBar] = foo.flatMap(f => bar.withFilter(b => b.isValid).map(b => new FooBar(f, b)))
 *   }
 * }}}
 *
 * Note that this problem can be difficult to debug because it only occurs if a self-type dependency is the first
 * element in the comprehension. However, it can easily be fixed by adding a dummy element to the comprehension like so:
 *
 *  {{{
 *   trait FooProvider {
 *     val foo: Singleton[Foo] = Singleton(() => constructFoo())
 *   }
 *
 *   trait FooBarProvider {
 *     this: FooProvider =>
 *
 *     import com.pacbio.common.dependency.MonadicSingletons._
 *
 *     val bar: Singleton[Bar] = Singleton(() => constructBar())
 *
 *     val foobar: Singleton[FooBar] = for {
 *       // This element constructs nothing, and only exists to ensure that the first element in the comprehension is
 *       // locally defined.
 *       u <- Singleton[Unit](() => ())
 *       f <- foo
 *       b <- bar
 *       if b.isValid
 *     } yield new FooBar(f, b)
 *   }
 * }}}
 *
 * This fix works fine, but is confusing to read. Until a better fix can be found, this code remains experimental.
 */
object MonadicSingletons {
  implicit def toMonadic[T](s: Singleton[T]): MonadicSingleton[T] = new MonadicSingleton[T](s)
  implicit def fromMonadic[T](m: MonadicSingleton[T]): Singleton[T] = m.singleton
}

private[dependency] class MonadicSingleton[+T](val singleton: Singleton[T]) {
  import MonadicSingletons._

  /**
   * Creates a new Singleton which will apply the function f after it is evaluated.
   */
  final def map[U](f: T => U): MonadicSingleton[U] = Singleton(() => f(this()))

  /**
   * Monadic bind function. Creates a new Singleton which will apply the function f after it is evaluated.
   */
  final def flatMap[U](f: T => MonadicSingleton[U]): MonadicSingleton[U] = Singleton(() => f(this())())

  /**
   * Creates a new Singleton that will apply the given assertion function after it is evaluated, raising an error if the
   * condition is not met.
   */
  final def filter(f: T => Boolean): MonadicSingleton[T] =
    Singleton(() => if(f(this())) this() else throw new AssertionError("Singleton assertion failed."))

  /**
   * Singleton contains exactly one value, so the difference between filter and withFilter is moot. This method is
   * included to make Singleton fit with scala's monad pattern, but is simply shorthand for filter(f).
   */
  final def withFilter(f: T => Boolean): MonadicSingleton[T] = filter(f)
}
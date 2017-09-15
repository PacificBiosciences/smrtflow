package com.pacbio.secondary.smrtlink.dependency

/**
  * For use in cake-pattern providers when a singleton instance is required.
  *
  * For singleton providers, the temptation is to use a val, like so:
  *
  * {{{
  *   trait MySingletonFooProvider {
  *     val foo: String = "foobar"
  *   }
  * }}}
  *
  * However, problems arise when singleton objects depend on other providers, like this:
  *
  * {{{
  *   trait MySingletonFooProvider {
  *     val bar: String
  *     val foo: String = "foo" + bar
  *   }
  *
  *   object MyProviders extends MySingletonFooProvider {
  *     override val bar: String = "bar"
  *   }
  * }}}
  *
  * In this case, one would expect that {{{MyProviders.foo == "foobar"}}}, but in fact you have
  * {{{MyProviders.foo == "nullbar"}}}. This is because the bar val is evaluated when the MySingletonFooProvider trait is
  * first defined, not when it is made concrete with MyProviders.
  *
  * (In theory, one should be able to solve this with scala's "lazy" vals, but in practice, the initialization of these
  * lazy vals is difficult to predict, and nulls are sometimes still returned.)
  *
  * SingletonProvider offers a solution to this problem, and can be used like so:
  *
  * {{{
  *   import com.pacbio.secondary.smrtlink.dependency.Singleton
  *
  *   trait MySingletonFooProvider {
  *     val bar: String
  *     val foo = Singleton(() => "foo" + bar)
  *   }
  *
  *   object MyProviders extends MySingletonFooProvider {
  *     override val bar: String = "bar"
  *   }
  * }}}
  *
  * Now the value of bar will be correctly set, and can be accessed safely as {{{MyProviders.bar() == "foobar"}}}.
  */
object Singleton {

  /**
    * Create a {{{Singleton}}} object from an instantiation function that takes no input. E.g.,
    * {{{val foo: Singleton[Foo] = Singleton(() => new Foo)}}}. This will be evaluated once, lazily, when the value is
    * accessed.
    */
  def apply[T](inst: () => T): Singleton[T] = new Singleton[T] {
    override val instantiate: () => T = inst
  }

  /**
    * Create a {{{Singleton}}} object from an already instantiated object. E.g.
    * {{{val foo: Singleton[String] = Singleton("foo")}}}. (This is just shorthand for {{{Singleton(() => "foo")}}}.)
    *
    * Note that if lazy evaluation isn't required, wrapping with a Singleton is generally not necessary. But this
    * constructor (unit function) is needed to make Singleton monadic.
    */
  def apply[T](obj: T): Singleton[T] = Singleton(() => obj)
}

/**
  * Represents a single value to be constructed by a provider. Evaluation is done lazily to ensure that all dependencies
  * are in place before construction is attempted.
  */
// TODO(smcclellan): Try to make this more monadic, so for-comprehensions can be used.
sealed trait Singleton[+T] {
  private[this] var singleton: Option[T] = None

  /**
    * Get the value of the {{{Singleton}}}. Note, this should only be called once the entire set of providers, with all
    * dependencies, is assembled, or inside the construction of another Singleton.
    */
  final def apply(): T = {
    if (singleton.isEmpty) {
      singleton = Some(instantiate())
    }
    singleton.get
  }

  /**
    * Binds the value of this {{{Singleton}}} to a {{{SetBinding}}}, so that it can be provided as part of a set. See
    * {{{SetBinding}}}. Calls to this method can be chained. E.g.:
    *
    * {{{
    *   class MyListener extends FooListener with BarListener
    *
    *   trait MyListenerProvider {
    *     val myListener: Singleton[MyListener] = Singleton(() => new MyListener)
    *       .bindToSet(FooListeners)
    *       .bindToSet(BarListeners)
    *   }
    * }}}
    */
  final def bindToSet(bind: SetBinding[_ >: T]): Singleton[T] = {
    bind.registerBinding(this)
    this
  }

  private[dependency] val instantiate: () => T
}

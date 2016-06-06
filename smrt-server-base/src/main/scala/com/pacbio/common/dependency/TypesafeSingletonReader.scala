package com.pacbio.common.dependency

import com.typesafe.config.{ConfigValue, ConfigFactory, Config}
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.language.implicitConversions

/**
 * Allows for reading Singletons directly from a Typesafe config file (e.g. application.conf).
 *
 * For instance, if you had the following configs in your application.conf
 *
 * {{{
 *   my-server {
 *     url = "my-server.nanofluidics.com"
 *     port = 9999
 *     timeout = 10 seconds
 *     currencies = ["USD", "GBP"]
 *
 *     db {
 *       url = "my-datastore.nanofluidics.com"
 *       port = 109
 *     }
 *   }
 * }}}
 *
 * You could create a provider for these configuration attributes like so:
 *
 * {{{
 *   case class MyDatabaseConfig(url: String, port: Int)
 *
 *   trait MyServerConfigProvider {
 *     import com.pacbio.common.dependency.TypesafeSingletonReader._
 *
 *     val myServerConfig = fromConfig("my-server")
 *
 *     // Reads the value at my-server.url as a String. The value must be configured or an error will occur.
 *     val url: Singleton[String] = myServerConfig.getString("url")
 *
 *     // Reads the value at my-server.port as an Int. If the value is not configured, the value 8080 will be provided.
 *     val port: Singleton[Int] = myServerConfig.getInt("port").orElse(8080)
 *
 *     // Reads the value at my-server.timeout as an Option[FiniteDuration]. If the value is not configured, None will
 *     // be provided.
 *     val timeout: Singleton[Option[FiniteDuration]] = myServerConfig.getDuration("timeout").optional
 *
 *     // Reads the value at my-server.currencies as a List[String]. If the value is not configured, Nil will be
 *     // provided.
 *     val currencies: Singleton[List[String]] = myServerConfig.getString("currencies").list
 *
 *     // Reads the values inside the sub-config at my-server.db as an Option[MyDatabaseConfig]. If the sub-config is
 *     // not present, None will be provided.
 *     val database: Singleton[Option[MyDatabaseConfig]] = myServerConfig.getObject("db"){ c =>
 *       // Reads the value at my-server.db.url as a String. The value must be configured or an error will occur.
 *       val url: Singleton[String] = c.getString("url")
 *
 *       // Reads the value at my-server.db.port as an Int. If the value is not configured, 15 will be provided.
 *       val port: Singleton[Int] = c.getInt("port").orElse(15)
 *
 *       Singleton(() => MyDatabaseConfig(url(), port()))
 *     }.optional
 *   }
 * }}}
 */
object TypesafeSingletonReader {
  /**
   * Reads values from the root config.
   */
  def fromConfig(): TypesafeSingletonReader = new TypesafeSingletonReader(ConfigFactory.load(), "")

  /**
   * If none of the methods {{{required}}}, {{{optional}}}, {{{orElse(default)}}}, or {{{list}}} are used, assume the
   * value is required.
   */
  // TODO(smcclellan): Does this work?
  implicit def assumeRequired[T](value: TypesafeSingletonValue[T]): Singleton[T] = value.required
}

/**
 * Reads Singletons from a Typesafe config file. (e.g. application.conf).
 */
class TypesafeSingletonReader(config: Config, root: String) {
  /**
   * Read from the sub-config at the given path.
   */
  def in(path: String): TypesafeSingletonReader = {
    if (!config.hasPath(path)) throw new IllegalArgumentException(s"No sub-config found at path $root$path")
    new TypesafeSingletonReader(config.getConfig(path), root + path + ".")
  }

  import TypesafeValueType._

  /**
   * Get the value at the given path as a String.
   */
  def getString(path: String): TypesafeSingletonValue[String] =
    new TypesafeSingletonValue[String](STRING, config, path, root)

  /**
   * Get the value at the given path as an Int.
   */
  def getInt(path: String): TypesafeSingletonValue[Int] = new TypesafeSingletonValue[Int](INT, config, path, root)

  /**
   * Get the value at the given path as a Long.
   */
  def getLong(path: String): TypesafeSingletonValue[Long] = new TypesafeSingletonValue[Long](LONG, config, path, root)

  /**
   * Get the value at the given path as a Boolean.
   */
  def getBoolean(path: String): TypesafeSingletonValue[Boolean] =
    new TypesafeSingletonValue[Boolean](BOOLEAN, config, path, root)

  /**
   * Get the value at the given path as a quantity of bytes, represented by a Long.
   */
  def getBytes(path: String): TypesafeSingletonValue[Long] = new TypesafeSingletonValue[Long](BYTES, config, path, root)

  /**
   * Get the value at the given path as a Double.
   */
  def getDouble(path: String): TypesafeSingletonValue[Double] =
    new TypesafeSingletonValue[Double](DOUBLE, config, path, root)

  /**
   * Get the value at the given path as a FiniteDuration.
   */
  def getDuration(path: String): TypesafeSingletonValue[FiniteDuration] =
    new TypesafeSingletonValue[FiniteDuration](DURATION(NANOSECONDS), config, path, root)

  /**
   * Reads an object (typically a case class) from the sub-config at the given path.
   *
   * Note: required, orElse, and optional forms are supported, but not list.
   */
  def getObject[T](path: String)(get: TypesafeSingletonReader => Singleton[T]) =
    new TypesafeSingletonValue[T](OBJECT[T](get), config, path, root)
}

private[dependency] object TypesafeValueType {
  sealed class TypesafeValueType[J, T](
      getter: Config => String => J,
      listGetter: Config => String => java.util.List[J],
      converter: J => T) {

    def get(config: Config, path: String): T = converter(getter(config)(path))

    def getOrElse(config: Config, path: String, default: T): T =
      if (config.hasPath(path)) converter(getter(config)(path)) else default

    def getOption(config: Config, path: String): Option[T] =
      if (config.hasPath(path)) Some(converter(getter(config)(path))) else None

    def getList(config: Config, path: String): List[T] =
      if (config.hasPath(path)) listGetter(config)(path).toList.map(converter) else Nil
  }

  case object STRING extends TypesafeValueType[String, String](_.getString, _.getStringList, x => x)
  case object INT extends TypesafeValueType[java.lang.Integer, Int](_.getInt, _.getIntList, x => x)
  case object LONG extends TypesafeValueType[java.lang.Long, Long](_.getLong, _.getLongList, x => x)
  case object BOOLEAN extends TypesafeValueType[java.lang.Boolean, Boolean](_.getBoolean, _.getBooleanList, x => x)
  case object BYTES extends TypesafeValueType[java.lang.Long, Long](_.getBytes, _.getBytesList, x => x)
  case object DOUBLE extends TypesafeValueType[java.lang.Double, Double](_.getDouble, _.getDoubleList, x => x)
  case class DURATION(u: TimeUnit) extends TypesafeValueType[java.lang.Long, FiniteDuration](
      (c: Config) => (p: String) => c.getDuration(p, u),
      (c: Config) => (p: String) => c.getDurationList(p, u),
      Duration(_, u)
  )
  case class OBJECT[T](get: TypesafeSingletonReader => Singleton[T]) extends TypesafeValueType[T, T](
      (c: Config) => (p: String) => get(new TypesafeSingletonReader(c, "").in(p))(),
      (c: Config) => (p: String) => throw new UnsupportedOperationException("List form not supported for type OBJECT"),
    x => x
  )
}

private[dependency] class TypesafeSingletonValue[T](
    typ: TypesafeValueType.TypesafeValueType[_, T],
    config: Config,
    path: String,
    root: String) {
  /**
   * Reads the value, throwing an exception if the path is not found.
   */
  def required: Singleton[T] = Singleton(() => typ.get(config, path))

  /**
   * Reads the value, providing a default value if the path is not found.
   */
  def orElse(default: T): Singleton[T] = Singleton(() => typ.getOrElse(config, path, default))

  /**
   * Reads the value as an Option, providing None if the path is not found.
   */
  def optional: Singleton[Option[T]] = Singleton(() => typ.getOption(config, path))

  /**
   * Reads a list of values, providing Nil if the path is not found.
   */
  def list: Singleton[List[T]] = Singleton(() => typ.getList(config, path))
}
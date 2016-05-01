package com.pacbio.common.database

import com.pacbio.common.dependency.Singleton

/**
 * Represents configuration information for a database.
 */
object DatabaseConfig {
  sealed trait DatabaseConfig

  case class Configured(url: String, driver: String) extends DatabaseConfig
  case object NotConfigured extends DatabaseConfig
}

/**
 * Companion object for DatabaseConfigProvider.
 */
object DatabaseConfigProvider {
  import DatabaseConfig._

  /**
   * Creates a DatabaseConfigProvider from an already instantiated DatabaseConfig.
   */
  def apply(config: DatabaseConfig): DatabaseConfigProvider = new DatabaseConfigProvider {
    override val databaseConfig: Singleton[DatabaseConfig] = Singleton(() => config)
  }
}

/**
 * Provides a DatabaseConfig for a specific database. Concrete providers must provide a concrete configuration.
 */
trait DatabaseConfigProvider {
  import DatabaseConfig._

  val databaseConfig: Singleton[DatabaseConfig]
}

/**
 * Provider that indicates that the database in question has not been configured.
 */
trait DatabaseNotConfiguredProvider extends DatabaseConfigProvider {
  import DatabaseConfig._

  override final val databaseConfig: Singleton[DatabaseConfig] = Singleton(() => NotConfigured)
}

/**
 * Companion object for TypesafeDatabaseConfigProvider.
 */
object TypesafeDatabaseConfigProvider {
  val DB_ROOT_PATH = "db"

  val DATABASE_URL_ATTR = "url"
  val DATABASE_DRIVER_ATTR = "driver"
}

/**
 * DatabaseConfigProvider that reads the configuration from application.conf. Concrete providers must provide a path to
 * the configuration information, relative to the root database configuration, "db".  For example, if the deployment
 * configs in application.conf contain the following:
 *
 * {{{
 *   db {
 *     user-database {
 *       url = "jdbc:sqlite:/home/jsmith/my-databases/users.sqlite"
 *       driver = "org.SQLite.JDBC"
 *     }
 *   }
 * }}}
 *
 * Then your TypesafeDatabaseConfigProvder should be defined like this:
 *
 * {{{
 *   object MyDatabaseConfigProvider extends TypesafeDatabaseConfigProvider {
 *     override val databaseConfigPath = "user-database"
 *   }
 * }}}
 *
 * If the path does not exist in the configuration, then {{{NotConfigured}}} will be provided.
 */
trait TypesafeDatabaseConfigProvider extends DatabaseConfigProvider {
  import DatabaseConfig._
  import TypesafeDatabaseConfigProvider._
  import com.pacbio.common.dependency.MonadicSingletons._
  import com.pacbio.common.dependency.TypesafeSingletonReader._

  val databaseConfigPath: Singleton[String]

  // Note that for-comprehensions using Singletons are experimental, and this pattern should not be reused elsewhere.

  val databaseConfig: Singleton[DatabaseConfig] = for {
    // Dummy singleton to prevent NPEs. See MonadicSingletons.
    x <- Singleton(() => ())

    p <- databaseConfigPath
    d <- fromConfig().in(DB_ROOT_PATH).getObject[DatabaseConfig](p) { conf =>
      Singleton(() => Configured(
        conf.getString(DATABASE_URL_ATTR).required(),
        conf.getString(DATABASE_DRIVER_ATTR).required()))
    }.orElse(NotConfigured)
  } yield d
}

/**
 * Provides a DatabaseConfig for each base-smrt-server database. By default, no databases are configured. Concrete
 * providers should define configured providers for any required databases.
 */
trait BaseSmrtServerDatabaseConfigProviders {
  // TODO(smcclellan): Split these into separate providers
  val logDaoDatabaseConfigProvider: DatabaseConfigProvider = new DatabaseNotConfiguredProvider {}
  val healthDaoDatabaseConfigProvider: DatabaseConfigProvider = new DatabaseNotConfiguredProvider {}
}
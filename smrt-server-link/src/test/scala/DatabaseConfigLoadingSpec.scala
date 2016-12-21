import org.specs2.mutable.Specification

import com.typesafe.config.ConfigFactory

/**
  * Created by mkocher on 12/19/16.
  */
class DatabaseConfigLoadingSpec extends Specification{

  "Load config from" should {
    "Sanity test to load db config" in {

      val c = ConfigFactory.load()

      val dbName = c.getString("smrtflow.test-db.properties.databaseName")

      val userName = c.getString("smrtflow.test-db.properties.user")
      val password = c.getString("smrtflow.test-db.properties.password")

      val expectedName = "smrtlink_test_user"

      userName === expectedName
      password === "password"
    }

  }
}

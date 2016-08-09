import com.pacbio.common.models.ModelImplicits
import com.pacbio.common.models.Models
import Models.IdAble
import org.specs2.mutable.Specification
import java.util.UUID

import spray.json._

import scala.language.implicitConversions


object TestModels {
  case class Person(i: IdAble, name: String)
}


trait CustomJsonProtocol extends DefaultJsonProtocol {
  import TestModels._
  import Models._
  import ModelImplicits._

  implicit object IdAbleFormat extends JsonFormat[IdAble] {
    def write(i: IdAble): JsValue =
      i match {
        case IntIdAble(n) => JsNumber(n)
        case UUIDIdAble(n) => JsString(n.toString)
      }
    def read(json: JsValue): IdAble = {
      json match {
        case JsString(n) => UUIDIdAble(UUID.fromString(n))
        case JsNumber(n) => IntIdAble(n.toInt)
        case _ => deserializationError("Expected IdAble Int or UUID format")
      }
    }
  }

  implicit val personFormat = jsonFormat2(Person)

}
object CustomJsonProtocol extends CustomJsonProtocol


class ModelsSpec extends Specification {

  import TestModels._
  import ModelImplicits._
  import CustomJsonProtocol._

  "Example/Test usage of IdAble" should {
    "Sanity Test to Serialize IdAble" in {

      val p1 = Person(1234, "Person A Int")
      val p2 = Person(UUID.randomUUID(), "Person B UUID")

      val people = Set(p1, p2)
      // Round trip from Person -> Json -> String -> Json -> Person
      val s1 = p1.toJson.toString
      val px1 = s1.parseJson.convertTo[Person]
      val px2 = p2.toJson.toString.parseJson.convertTo[Person]

      // If we've got here, everything is good
      people.size must beEqualTo(2)
    }
  }
}

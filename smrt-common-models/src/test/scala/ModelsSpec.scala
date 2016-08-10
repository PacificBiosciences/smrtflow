import com.pacbio.common.models.{CommonModels,CommonModelImplicits}
import org.specs2.mutable.Specification
import java.util.UUID

import spray.json._

import scala.language.implicitConversions


object TestModels {
  import CommonModels._
  case class Person(i: IdAble, name: String)
}


trait CustomJsonProtocol extends DefaultJsonProtocol {
  import TestModels._
  import CommonModels._
  import CommonModelImplicits._

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
  import CommonModelImplicits._
  import CustomJsonProtocol._

  "Example/Test usage of IdAble" should {
    "Sanity Test to Serialize IdAble" in {

      val us = "1770b34b-4624-405f-85c1-04198b078586"
      val uuid = UUID.fromString(us)
      val p1 = Person(1234, "Person A Int")
      val p2 = Person(uuid, "Person B UUID")

      val people = Set(p1, p2)
      // Round trip from Person -> Json -> String -> Json -> Person
      val s1 = p1.toJson.toString
      val px1 = s1.parseJson.convertTo[Person]
      val px2 = p2.toJson.toString.parseJson.convertTo[Person]

      p2.i.toIdString must beEqualTo(us)
      // If we've got here, everything is good
      people.size must beEqualTo(2)
    }
  }
}


import com.pacbio.secondary.analysis.jobs.OptionTypes
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.jobs.{JobModels,OptionTypes}

import org.specs2.mutable.Specification

import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration.FiniteDuration


class JsonProtocolsSpec extends Specification  {

  import OptionTypes._

  sequential

  var serviceOpts = Seq(
    ServiceTaskBooleanOption("id-a", true, BOOL.optionTypeId),
    ServiceTaskIntOption("id-b", 2001, INT.optionTypeId),
    ServiceTaskDoubleOption("id-c", 3.14, FLOAT.optionTypeId),
    ServiceTaskStrOption("id-d", "asdf", STR.optionTypeId),
    ServiceTaskStrOption("id-e", "B", CHOICE.optionTypeId),
    ServiceTaskIntOption("id-f", 2, CHOICE_INT.optionTypeId),
    ServiceTaskDoubleOption("id-g", 0.1, CHOICE_FLOAT.optionTypeId))

  "Test serialization of models" should {
    "Service task options" in {
      import SmrtLinkJsonProtocols._
      val j = serviceOpts.map(_.asInstanceOf[ServiceTaskOptionBase].toJson)
      val o = j.map(_.convertTo[ServiceTaskOptionBase])
      o.size must beEqualTo(7)
      val opt1 = o(0).asInstanceOf[ServiceTaskBooleanOption]
      opt1.optionTypeId must beEqualTo(BOOL.optionTypeId)
      opt1.value must beTrue
      val opt2 = o(1).asInstanceOf[ServiceTaskIntOption]
      opt2.optionTypeId must beEqualTo(INT.optionTypeId)
      opt2.value must beEqualTo(2001)
      val opt3 = o(2).asInstanceOf[ServiceTaskDoubleOption]
      opt3.optionTypeId must beEqualTo(FLOAT.optionTypeId)
      opt3.value must beEqualTo(3.14)
      val opt4 = o(3).asInstanceOf[ServiceTaskStrOption]
      opt4.optionTypeId must beEqualTo(STR.optionTypeId)
      opt4.value must beEqualTo("asdf")
      val opt5 = o(4).asInstanceOf[ServiceTaskStrOption]
      opt5.optionTypeId must beEqualTo(CHOICE.optionTypeId)
      opt5.value must beEqualTo("B")
      val opt6 = o(5).asInstanceOf[ServiceTaskIntOption]
      opt6.optionTypeId must beEqualTo(CHOICE_INT.optionTypeId)
      opt6.value must beEqualTo(2)
      val opt7 = o(6).asInstanceOf[ServiceTaskDoubleOption]
      opt7.optionTypeId must beEqualTo(CHOICE_FLOAT.optionTypeId)
      opt7.value must beEqualTo(0.1)
    }
  }
}

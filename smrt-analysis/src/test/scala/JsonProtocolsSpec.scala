
import com.pacbio.secondary.analysis.jobs.{JobModels,OptionTypes,SecondaryJobProtocols}

import org.specs2.mutable.Specification

import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration.FiniteDuration


class JsonProtocolsSpec extends Specification  {

  import JobModels._
  import OptionTypes._
  import SecondaryJobProtocols._

  sequential

  "Test serialization of models" should {
    "Service task options" in {
      var serviceOpts = Seq(
        ServiceTaskBooleanOption("id-a", true, BOOL.optionTypeId),
        ServiceTaskIntOption("id-b", 2001, INT.optionTypeId),
        ServiceTaskDoubleOption("id-c", 3.14, FLOAT.optionTypeId),
        ServiceTaskStrOption("id-d", "asdf", STR.optionTypeId),
        ServiceTaskStrOption("id-e", "B", CHOICE.optionTypeId),
        ServiceTaskIntOption("id-f", 2, CHOICE_INT.optionTypeId),
        ServiceTaskDoubleOption("id-g", 0.1, CHOICE_FLOAT.optionTypeId))
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
    "Pipeline options" in {
      val o1 = PipelineBooleanOption("id-a", "Boolean", true, "Boolean Option")
      val o2 = PipelineIntOption("id-b", "Int", 2001, "Integer Option")
      val o3 = PipelineDoubleOption("id-c", "Double", 3.14, "Double  Option")
      val o4 = PipelineStrOption("id-d", "String", "asdf", "String Option")
      val o5 = PipelineChoiceStrOption("id-e", "String Choice", "B", "String Choice Option", Seq("A", "B", "C"))
      val o6 = PipelineChoiceIntOption("id-f", "Int Choice", 2, "Int Choice Option", Seq(1,2,3))
      val o7 = PipelineChoiceDoubleOption("id-g", "Double", 0.1, "Double Choice Option", Seq(0.01, 0.1, 1.0))
      val taskOpts = Seq(o1, o2, o3, o4, o5, o6, o7)
      // boolean
      // we have to do a lot of type conversion for this to even compile
      var j = o1.asInstanceOf[PipelineBaseOption].toJson
      println(j)
      val oj1 = j.convertTo[PipelineBaseOption].asInstanceOf[PipelineBooleanOption]
      oj1.value must beTrue
      j = o1.asServiceOption.asInstanceOf[ServiceTaskOptionBase].toJson
      val oj1b = j.convertTo[ServiceTaskOptionBase].asInstanceOf[ServiceTaskBooleanOption]
      oj1b.value must beTrue
      // integer
      j = o2.asInstanceOf[PipelineBaseOption].toJson
      val oj2 = j.convertTo[PipelineBaseOption].asInstanceOf[PipelineIntOption]
      oj2.value must beEqualTo(2001)
      // double
      j = o3.asInstanceOf[PipelineBaseOption].toJson
      val oj3 = j.convertTo[PipelineBaseOption].asInstanceOf[PipelineDoubleOption]
      oj3.value must beEqualTo(3.14)
      j = o3.asServiceOption.asInstanceOf[ServiceTaskOptionBase].toJson
      val oj3b = j.convertTo[ServiceTaskOptionBase].asInstanceOf[ServiceTaskDoubleOption]
      oj3b.value must beEqualTo(3.14)
      // string
      j = o4.asInstanceOf[PipelineBaseOption].toJson
      val oj4 = j.convertTo[PipelineBaseOption].asInstanceOf[PipelineStrOption]
      oj4.value must beEqualTo("asdf")
      // string choice
      j = o5.asInstanceOf[PipelineBaseOption].toJson
      val oj5 = j.convertTo[PipelineBaseOption].asInstanceOf[PipelineChoiceStrOption]
      oj5.value must beEqualTo("B")
      oj5.choices must beEqualTo(Seq("A","B","C"))
      val oj5b = oj5.applyValue("C")
      oj5b.value must beEqualTo("C")
      oj5.applyValue("D") must throwA[UnsupportedOperationException]
      // int choice
      j = o6.asInstanceOf[PipelineBaseOption].toJson
      val oj6 = j.convertTo[PipelineBaseOption].asInstanceOf[PipelineChoiceIntOption]
      oj6.value must beEqualTo(2)
      oj6.choices must beEqualTo(Seq(1,2,3))
      val oj6b = oj6.applyValue(3)
      oj6b.value must beEqualTo(3)
      oj6.applyValue(0) must throwA[UnsupportedOperationException]
      // double choice
      j = o7.asInstanceOf[PipelineBaseOption].toJson
      val oj7 = j.convertTo[PipelineBaseOption].asInstanceOf[PipelineChoiceDoubleOption]
      oj7.value must beEqualTo(0.1)
      oj7.choices must beEqualTo(Seq(0.01, 0.1, 1.0))
      val oj7b = oj7.applyValue(1.0)
      oj7b.value must beEqualTo(1.0)
      oj7.applyValue(0.9) must throwA[UnsupportedOperationException]
    }
    "Pipeline presets" in {
      var opts = Seq(
        ServiceTaskBooleanOption("id-a", true, BOOL.optionTypeId),
        ServiceTaskIntOption("id-b", 2001, INT.optionTypeId))
      var taskOpts = Seq(
        ServiceTaskDoubleOption("id-c", 3.14, FLOAT.optionTypeId),
        ServiceTaskStrOption("id-d", "Hello, world", STR.optionTypeId),
        ServiceTaskStrOption("id-e", "A", CHOICE.optionTypeId))
      var pp = PipelineTemplatePreset("preset-id-01", "pipeline-id-01", opts, taskOpts)
      var j = pp.toJson
      var ppp = j.convertTo[PipelineTemplatePreset]
      //ppp must beEqualTo(pp)
      pp.presetId must beEqualTo(ppp.presetId)
      pp.pipelineId must beEqualTo(ppp.pipelineId)
      ppp.options.toList must beEqualTo(opts)
      ppp.taskOptions.toList must beEqualTo(taskOpts)
    }
  }
}

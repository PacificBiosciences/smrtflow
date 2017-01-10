
import com.pacbio.secondary.smrtlink.client.ClientUtils
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.jobs.{JobModels,OptionTypes}

import org.specs2.mutable.Specification

import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration.FiniteDuration


class ClientUtilsSpec extends Specification with ClientUtils {

  sequential

  "Test ClientUtils utility functions" should {
    "Pipeline utilities" in {
      import OptionTypes._
      import JobModels._
      val taskOpts = Seq(
        PipelineBooleanOption("id-a", "Boolean", true, "Boolean Option"),
        PipelineIntOption("id-b", "Int", 2001, "Integer Option"),
        PipelineDoubleOption("id-c", "Double", 3.14, "Double  Option"),
        PipelineStrOption("id-d", "String", "asdf", "String Option"),
        PipelineChoiceStrOption("id-e", "String Choice", "B", "String Choice Option", Seq("A", "B", "C")),
        PipelineChoiceIntOption("id-f", "Int Choice", 2, "Int Choice Option", Seq(1,2,3)),
        PipelineChoiceDoubleOption("id-g", "Double", 0.1, "Double Choice Option", Seq(0.01, 0.1, 1.0))
      )
      val serviceOpts = getPipelineServiceOptions(taskOpts)
      serviceOpts must beEqualTo(Seq(
        ServiceTaskBooleanOption("id-a", true, BOOL.optionTypeId),
        ServiceTaskIntOption("id-b", 2001, INT.optionTypeId),
        ServiceTaskDoubleOption("id-c", 3.14, FLOAT.optionTypeId),
        ServiceTaskStrOption("id-d", "asdf", STR.optionTypeId),
        ServiceTaskStrOption("id-e", "B", CHOICE.optionTypeId),
        ServiceTaskIntOption("id-f", 2, CHOICE_INT.optionTypeId),
        ServiceTaskDoubleOption("id-g", 0.1, CHOICE_FLOAT.optionTypeId)))
    }
  }
}

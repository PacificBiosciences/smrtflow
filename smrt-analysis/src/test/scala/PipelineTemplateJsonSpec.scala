import java.nio.file.Paths

import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import com.pacbio.secondary.analysis.pipelines.JsonPipelineTemplatesLoader
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobs.{SecondaryJobJsonProtocol, PipelineTemplateOptionProtocol}

import spray.json._

/**
 * Sanity test for loading PipelineTemplates from avro
 * @author mkocher
 */
class PipelineOptionsJsonSpec extends Specification with PipelineTemplateOptionProtocol with LazyLogging{

  sequential

  "Test task option JSON serialization" should {
    "Convert all types to and from JSON" in {
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
      // integer
      j = o2.asInstanceOf[PipelineBaseOption].toJson
      val oj2 = j.convertTo[PipelineBaseOption].asInstanceOf[PipelineIntOption]
      oj2.value must beEqualTo(2001)
      // double
      j = o3.asInstanceOf[PipelineBaseOption].toJson
      val oj3 = j.convertTo[PipelineBaseOption].asInstanceOf[PipelineDoubleOption]
      oj3.value must beEqualTo(3.14)
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
  }
}


class PipelineTemplateJsonSpec extends Specification with SecondaryJobJsonProtocol with LazyLogging{

  sequential

  "Test pipeline template JSON serialization" should {
    "Smoke test for json pipeline" in {
      val name = "pipeline-templates/pbsmrtpipe.pipelines.sa3_ds_resequencing_fat_pipeline_template.json"
      val path = getClass.getResource(name)
      val p = Paths.get(path.toURI)
      val pipelineTemplate = JsonPipelineTemplatesLoader.loadFrom(p)
      // this is the json representation
      logger.info(s"Pipeline template $pipelineTemplate")
      pipelineTemplate.id.toString mustEqual "pbsmrtpipe.pipelines.sa3_ds_resequencing_fat"
      val j = pipelineTemplate.toJson
      val ptFromJson = j.convertTo[PipelineTemplate]
      ptFromJson.id.toString mustEqual pipelineTemplate.id.toString
    }
    "Load json templates from dir" in {
      val name = "pipeline-templates"
      val path = getClass.getResource(name)
      val p = Paths.get(path.toURI)
      logger.info(s"Loading pipeline templates from $p")
      val pts = JsonPipelineTemplatesLoader.loadFromDir(p)
      pts.length mustEqual 3
    }
    "Test all task option types" in {
      val name = "pipeline-templates/example_pipeline_template_01.json"
      val path = getClass.getResource(name)
      val ppath = Paths.get(path.toURI)
      val pipelineTemplate = JsonPipelineTemplatesLoader.loadFrom(ppath)
      val j = pipelineTemplate.toJson
      val p = j.convertTo[PipelineTemplate]
      val tOpts = p.taskOptions.map(o => (o.id, o)).toMap
      tOpts("pbsmrtpipe.task_options.alpha") must haveClass[PipelineStrOption]
      tOpts("pbsmrtpipe.task_options.beta") must haveClass[PipelineBooleanOption]
      tOpts("pbsmrtpipe.task_options.gamma") must haveClass[PipelineIntOption]
      tOpts("pbsmrtpipe.task_options.delta") must haveClass[PipelineDoubleOption]
      tOpts("pbsmrtpipe.task_options.a") must haveClass[PipelineChoiceStrOption]
      tOpts("pbsmrtpipe.task_options.b") must haveClass[PipelineChoiceIntOption]
      tOpts("pbsmrtpipe.task_options.c") must haveClass[PipelineChoiceDoubleOption]
    }
  }
}

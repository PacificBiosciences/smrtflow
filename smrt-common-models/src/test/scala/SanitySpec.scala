import org.specs2.mutable.Specification

import com.pacbio.common.models.Constants

/**
  * Sanity test for checking the Data model generated from XSD
  * Created by mkocher on 6/29/15.
  */
class SanitySpec extends Specification {

  "Sanity test for getting the SMRTFlow version" should {
    "Should be able to load the Smrtflow version" in {
      val smrtflowVersion = Constants.SMRTFLOW_VERSION
      println(s"Smrtflow Version $smrtflowVersion")
      true should beTrue
    }
  }
}

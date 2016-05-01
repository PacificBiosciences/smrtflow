import com.pacificbiosciences.pacbiocollectionmetadata.Primary.OutputOptions.TransferResource
import org.specs2.mutable.Specification

/**
 * Sanity test for checking the Data model generated from XSD
 * Created by mkocher on 6/29/15.
 */
class SanitySpec extends Specification{

  "Sanity test for loading SubreadSet" should {
    "Should be able to load" in {
      true should beTrue
    }
    "Test TransferResource" in {

       val tr = new TransferResource()
       tr.setDescription("A Description")
       tr.setName("Name of Transfer Resource")
       tr.setId("My-id")
       tr.setTransferScheme("NFS")
      true must beTrue
    }
  }
}

import java.net.URL
import java.util.UUID

import akka.actor.ActorSystem
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.models.EngineJobEntryPoint
import com.pacbio.secondaryinternal.IOUtils
import com.pacbio.secondaryinternal.client.InternalAnalysisServiceClient
import org.specs2.matcher.MatchResult
import org.specs2.mutable.Specification

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.language.reflectiveCalls
import scala.util.{Failure, Success, Try}

class ConditionalJobSpec  extends Specification {

  "ITG-159 regression test" should {
    "Incorrect job type should throw a more helpful error message" in {
      val xs = "condId,host,jobId\nA_30622,smrtlink-beta,30622"
      val cs = IOUtils.parseConditionCsv(scala.io.Source.fromString(xs))
      val system = ActorSystem()
      try {
        // client for talking to smrtlink-beta
        val client = new InternalAnalysisServiceClient(new URL("http://smrtlink-mock:1234"))(system)
        // get the bad entry -- happens to be first
        val sc = cs.head
        // should not make it past here
        val dmt = DataSetMetaTypes.Subread
        val emptyEntryPoints = List()
        Try(client.getFirstDataSetFromEntryPoint(sc, emptyEntryPoints, dmt)) match {
          case Failure(t) => t.getMessage mustEqual client.noDataSetErrorMessage(sc, dmt)
        }
      }
      finally {
        system.shutdown()
      }
    }
    "Correct job type should work" in {
      val xs = "condId,host,jobId\nA_30622,smrtlink-beta,30622"
      val cs = IOUtils.parseConditionCsv(scala.io.Source.fromString(xs))
      val system = ActorSystem()
      try {
        // client for talking to smrtlink-beta
        val client = new InternalAnalysisServiceClient(new URL("http://smrtlink-mock:1234"))(system)
        // get the bad entry -- happens to be first
        val sc = cs.head
        // should not make it past here
        val dmt = DataSetMetaTypes.Subread
        val uuid = UUID.randomUUID()
        val validEntryPoints = List(EngineJobEntryPoint(sc.jobId, uuid, dmt.toString))
        Await.result(client.getFirstDataSetFromEntryPoint(sc, validEntryPoints, dmt), Duration(1, "seconds")) match {
          case v: UUID => v mustEqual uuid
        }
      }
      finally {
        system.shutdown()
      }
    }
  }
}

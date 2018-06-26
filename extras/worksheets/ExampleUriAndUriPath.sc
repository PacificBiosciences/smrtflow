// This can be loaded in to Intellij, or ammonite directly
// E.g., amm -p /Users/mkocher/repos/smrtflow/extras/worksheets/ExampleUriAndUriPath.sc
// import $ivy.`com.typesafe.akka::akka-http-core:10.0.11` // Uncomment to use directly in amm

import akka.http.scaladsl.model._

// This can be used as the basis of
val pe = Uri.Path.Empty
val p0 = Uri.Path./

val p1 = Uri.Path("p1")

val root = pe / "SMRTLink" / "1.0.0"

println(p1)

val p2 = p1 / "stuff" / "more-stuff"

// This will generate an double leading slash
val pr = p0 / "stuff" / "more-stuff"

// This is what you want
val p3 = pe / "stuff" / "more-stuff"

// Example of Making a Root Slash

val p4  = pe / "stuff" / "more-stuff"

println(p4)

// Uri construction
// Creating a URI from a Uri.Path is a bit clumsy or verbose. It's often done via Uri.from

val u0 = Uri./
println(u0)

val u1 = Uri.from("https", host="smrtlink-bihourly", port=8234)

// This now has the "full" form
val up1 = u1.path

val up2 = u1.withPath(p3)

// This is a workaround to get the an "empty" Uri with the correct path
val u2 = Uri(p4.toString())

// Then add query Params.
val u3 = u2.withRawQueryString("alpha=1&beta=2")

// This will handle the URL encoding of the Query paramters
val q1 = Uri.Query("alpha" -> "1", "beta" -> "3", "gamma" -> "a+b")
val u4 = u2.withQuery(q1)

val ROOT_PATH: Option[Uri.Path] = Some(root)

// Mimic the Client API
def toUri(path: Uri.Path): Uri = {

  val x0: Uri.Path = ROOT_PATH.getOrElse(Uri.Path.Empty)
  val x1 = x0 ++ path
  //
  Uri(x1.toString())
}

val m1 = toUri(p4)

logLevel := Level.Warn

addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.9.3")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")

// Code coverage support and plugin for (optional) Coveralls.io
//addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.3.5")

//addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.1.0")

// Show dependency Graph
//addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")

// Shows which libs can be updated
//addSbtPlugin("com.sksamuel.sbt-versions" % "sbt-versions" % "0.2.0")

addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.10")
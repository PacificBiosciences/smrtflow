logLevel := Level.Warn

addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.6.6")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")

addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "1.1.1")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

// Code coverage support and plugin for (optional) Coveralls.io
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.3.5")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.1.0")

// Scalastyle.org checker via `sbt scalastyle`
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.8.0")

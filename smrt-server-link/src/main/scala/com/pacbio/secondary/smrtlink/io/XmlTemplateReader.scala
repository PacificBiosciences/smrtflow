package com.pacbio.secondary.smrtlink.io

import java.io.{ByteArrayInputStream, File, FileInputStream, InputStream}
import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.regex.Pattern

import scala.xml.transform.{RewriteRule, RuleTransformer}
import scala.xml.{Node, XML}

// TODO(smcclellan): Add scaladoc, unittests

object XmlTemplateReader {

  type Subs = Map[String, () => Any]

  private trait Scope {
    def substituteAll(template: Node, subs: Subs): Node
  }

  /**
   * Computes the value for each substitution once, and substitutes that same value everywhere.
   */
  private case object GLOBAL extends Scope {
    override def substituteAll(template: Node, subs: Subs): Node = {
      var xmlString = template.mkString
      for ((find, replace) <- subs) {
        val repString = replace().toString
        xmlString = xmlString.replaceAllLiterally(find, repString)
      }
      XML.loadString(xmlString)
    }
  }

  /**
   * Computes a new value for every time the substitution string is found.
   */
  private case object INSTANCE extends Scope {
    override def substituteAll(template: Node, subs: Subs): Node = {
      var xmlString = template.mkString
      for ((find, replace) <- subs) {
        while (xmlString contains find) {
          val repString = replace().toString
          xmlString = xmlString.replaceFirst(Pattern.quote(find), repString)
        }
      }
      XML.loadString(xmlString)
    }
  }

  /**
   * Only performs substitutions inside nodes with the given label, and computes a new set of
   * substitution values for each node.
   */
  private final case class NODE(label: String) extends Scope {
    override def substituteAll(template: Node, subs: Subs): Node = {
      val rule = new RewriteRule {
        override def transform(n: Node): Seq[Node] = if (n.label == label) {
          var elemString = n.mkString
          for ((find, replace) <- subs) {
            val repString = replace().toString
            elemString = elemString.replaceAllLiterally(find, repString)
          }
          XML.loadString(elemString)
        } else n
      }
      val transformer = new RuleTransformer(rule)
      transformer(template)
    }
  }

  sealed abstract class Runner(input: InputStream) {
    private[XmlTemplateReader] var template: Node = XML.load(input)

    def globally(): ScopedRunner = new ScopedRunner(GLOBAL, this) {}
    def perInstance(): ScopedRunner = new ScopedRunner(INSTANCE, this) {}
    def perNode(label: String): ScopedRunner = new ScopedRunner(NODE(label), this) {}

    def result(): Node = template
  }

  sealed abstract class ScopedRunner(scope: Scope, runner: Runner) {
    def substituteMap(subs: Subs): Runner = {
      runner.template = scope.substituteAll(runner.template, subs)
      runner
    }

    def substituteAll(subs: (String, () => Any)*): Runner = {
      substituteMap(Map(subs:_*))
    }

    def substitute(find: String, replace: => Any): Runner = {
      substituteMap(Map(find -> (() => replace)))
    }
  }

  def fromStream(stream: InputStream): Runner = new Runner(stream) {}

  def fromFile(file: File): Runner = fromStream(new FileInputStream(file))

  def fromFile(file: Path): Runner = fromFile(file.toFile)

  def fromFile(file: String): Runner = fromFile(Paths.get(file))

  def fromUrl(url: URL): Runner = fromStream(url.openStream())

  def from(input: String): Runner = fromStream(new ByteArrayInputStream(input.getBytes("UTF-8")))

  def from(input: Node): Runner = from(input.mkString)
}
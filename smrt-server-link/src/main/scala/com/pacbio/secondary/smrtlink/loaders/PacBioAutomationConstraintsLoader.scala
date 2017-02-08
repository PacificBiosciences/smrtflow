package com.pacbio.secondary.smrtlink.loaders

import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.xml.bind.{JAXBContext, Marshaller}
import spray.json._

import com.pacificbiosciences.pacbioautomationconstraints.PacBioAutomationConstraints
import org.eclipse.persistence.jaxb.{MarshallerProperties, UnmarshallerProperties}

/**
  * Created by mkocher on 2/6/17.
  */
trait PacBioAutomationConstraintsLoader {

  private def toUnMarshaller(context: JAXBContext, path: Path) = {
    val unmarshaller = context.createUnmarshaller()
    unmarshaller.unmarshal(path.toFile)
  }

  private def contextToMarshaller(jAXBContext: JAXBContext):Marshaller = {
    val jmarshaller = jAXBContext.createMarshaller()
    jmarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
    jmarshaller.setProperty(MarshallerProperties.JSON_INCLUDE_ROOT, false)
    jmarshaller.setProperty(MarshallerProperties.MEDIA_TYPE, "application/json")
    jmarshaller
  }

  def pacBioAutomationToJson(pacBioAutomationConstraints: PacBioAutomationConstraints): JsValue = {
    val jAXBContext = JAXBContext.newInstance(classOf[PacBioAutomationConstraints])
    val outStream = new ByteArrayOutputStream()
    contextToMarshaller(jAXBContext).marshal(pacBioAutomationConstraints, outStream)
    outStream.toString.parseJson
  }

  def loadFrom(path: Path): PacBioAutomationConstraints = {
    toUnMarshaller(JAXBContext.newInstance(classOf[PacBioAutomationConstraints]), path).asInstanceOf[PacBioAutomationConstraints]
  }

}

object PacBioAutomationConstraintsLoader extends PacBioAutomationConstraintsLoader

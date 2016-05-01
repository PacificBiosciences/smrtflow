package com.pacbio.secondary.analysis.legacy

import java.nio.file.Path
import javax.xml.bind.JAXBContext

/**
 * Legacy tool for loading
 * Created by mkocher on 6/30/15.
 */
object ReferenceInfoUtils {

  def loadFrom(path: Path): ReferenceInfoType = {
    val context = JAXBContext.newInstance(classOf[ReferenceInfoType])
    val unmarshaller = context.createUnmarshaller()
    unmarshaller.unmarshal(path.toFile).asInstanceOf[ReferenceInfoType]
  }
}

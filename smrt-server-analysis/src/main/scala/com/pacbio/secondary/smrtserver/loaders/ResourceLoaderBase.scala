package com.pacbio.secondary.smrtserver.loaders

/**
  * Created by mkocher on 7/12/16.
  */
trait ResourceLoaderBase[T] {

  def loadFromString(sx: String): T

  /**
    * Default message for loading a resource
    *
    * @param xs Object
    * @return
    */
  def loadMessage(xs: T): String = {
    s"Loaded ${xs.toString}"
  }

}

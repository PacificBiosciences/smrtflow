package com.pacbio.secondary.smrtlink.loaders

/**
  * Created by mkocher on 7/13/16.
  */
trait JsonAndEnvResourceLoader[T] extends JsonResourceLoader[T] with EnvResourceLoader[T]{

  def resources: Seq[T] = loadResources ++ loadResourcesFromEnv
}

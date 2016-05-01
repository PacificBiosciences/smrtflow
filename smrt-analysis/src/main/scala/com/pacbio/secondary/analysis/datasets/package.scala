package com.pacbio.secondary.analysis

/**
 *
 * Created by mkocher on 9/29/15.
 */
package object datasets {

  case class InValidDataSetError(msg: String) extends Exception(msg)
}

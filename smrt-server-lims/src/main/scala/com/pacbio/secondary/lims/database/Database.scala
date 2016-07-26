package com.pacbio.secondary.lims.database

import com.pacbio.secondary.lims.LimsSubreadSet
import spray.json.JsValue


/**
 * All of the public DAO methods
 *
 * This trait is the main DB-agnostic interface that the rest of the codebase relies on. The Cake
 * pattern is used to build up the desired DB implementation at runtime.
 */
trait Database {

  /**
   * General alias support
   * @param alias
   * @param uuid
   * @param typ
   */
  def setAlias(alias: String, uuid: String, typ: String): Unit

  def delAlias(alias: String): Unit

  /**
   * Store, retrieve and manipulate LIMS versions of SubreadSet
   */
  def setSubread(uuid: String, expid: Int, runcode: String, json: JsValue): String

  def subreadByAlias(alias: String): LimsSubreadSet

  def subreadsByExperiment(e: Int): Seq[LimsSubreadSet]

  def subreadsByRunCode(rc: String): Seq[LimsSubreadSet]

  def subreads(pks: Seq[String]): Seq[LimsSubreadSet]

  def subread(pk: String): LimsSubreadSet
}
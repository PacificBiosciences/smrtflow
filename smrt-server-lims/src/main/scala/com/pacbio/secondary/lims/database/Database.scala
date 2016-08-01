package com.pacbio.secondary.lims.database

import java.util.UUID

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
  def setAlias(alias: String, uuid: UUID, typ: String): Unit

  def delAlias(alias: String): Boolean

  /**
   * Store, retrieve and manipulate LIMS versions of SubreadSet
   */
  def setSubread(l: LimsSubreadSet): String

  def subreadByAlias(alias: String): LimsSubreadSet

  def subreadsByExperiment(e: Int): Seq[LimsSubreadSet]

  def subreadsByRunCode(rc: String): Seq[LimsSubreadSet]

  def subreads(uuids: Seq[UUID]): Seq[LimsSubreadSet]

  def subread(uuid: UUID): Option[LimsSubreadSet]
}
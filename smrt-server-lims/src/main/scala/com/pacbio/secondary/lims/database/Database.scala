package com.pacbio.secondary.lims.database

import com.pacbio.secondary.lims.LimsYml


/**
 * All of the public DAO methods
 *
 * This trait is the main DB-agnostic interface that the rest of the codebase relies on. The Cake
 * pattern is used to build up the desired DB implementation at runtime.
 */
trait Database {

  def setLimsYml(v: LimsYml): String

  def setAlias(alias: String, uuid: String): Unit

  def delAlias(alias: String): Unit

  /**
   * Converts from an arbitrary alias to the matching lims.yml files
   */
  def getByAlias(alias: String): LimsYml

  def getByExperiment(e: Int): Seq[LimsYml]

  def getByRunCode(rc: String): Seq[LimsYml]

  def getLimsYml(pks: Seq[(Int, String)]): Seq[LimsYml]

  def getLimsYml(pk: (Int, String)): LimsYml
}
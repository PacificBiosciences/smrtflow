package com.pacbio.secondary.lims.database

import com.pacbio.secondary.lims.LimsYml


/**
 * All of the public DAO methods
 *
 * This trait is the main DB-agnostic interface that the rest of the codebase relies on. The Cake
 * patter is used to build up the desired DB implementation at runtime.
 *
 * See DESIGN.md#Database for example production and test use
 *
 */
trait Database {

  def setLimsYml(v: LimsYml): String

  /**
   * Converts from an arbitrary alias to the matching lims.yml files
   */
  def getByAlias(alias: String): Seq[Int]

  def getByExperiment(uuid: Int): Seq[Int]

  def getByRunCode(uuid: String): Seq[Int]

  def getLimsYml(q: Seq[Int]): Seq[LimsYml]

  def getLimsYml(q: Int): LimsYml


}






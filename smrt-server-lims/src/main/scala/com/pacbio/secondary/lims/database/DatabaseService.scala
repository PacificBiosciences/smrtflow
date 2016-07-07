package com.pacbio.secondary.lims.database

import com.pacbio.secondary.lims.LimsYml


/**
 * All of the public DAO methods
 *
 * This trait is the main DB-agnostic interface that the rest of the codebase relies on.
 */
trait DatabaseService {

  def setLimsYml(v: LimsYml): String

  def getByUUID(uuid: String): String

  def getByUUIDPrefix(uuid: String): String
}






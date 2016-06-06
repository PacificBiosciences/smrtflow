package com.pacbio.secondary.analysis.engine

import java.nio.file.{Path, Files, Paths}

import com.typesafe.config.{Config, ConfigFactory}

import scala.util.Try

/**
 *
 * Engine Configuration Settings
 *
 * Created by mkocher on 6/19/15.
 *
 * The naming is important. maxWorkers -> max-workers in the application.conf
 *
 * max-workers is the max number of Engine Workers that will be created
 * pbToolsEnv path to env which will be sourced before external tools (e.g., pbsmrtpipe, samtools, sawriter) will be called
 * pbRootJobDir is the root path to write job output to. The subdirectory will be created for each job type
 *
 */
case class EngineConfig(
    maxWorkers: Int,
    pbToolsEnv: String,
    pbRootJobDir: String,
    debugMode: Boolean)


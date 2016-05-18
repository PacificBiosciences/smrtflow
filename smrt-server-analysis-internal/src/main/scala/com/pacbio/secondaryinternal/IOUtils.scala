package com.pacbio.secondaryinternal

import java.nio.file.Path

import com.pacbio.secondaryinternal.models._
import com.github.tototoshi.csv._


object IOUtils {

  def parseConditionCsv(path: Path): Seq[ServiceCondition] = {
    val reader = CSVReader.open(path.toFile)

    val datum = reader.allWithHeaders()

    datum.map(x => ServiceCondition(x("condId"), x("host"), 8081, x("jobId").toInt))
  }
}
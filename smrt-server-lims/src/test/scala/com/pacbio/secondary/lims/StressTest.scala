package com.pacbio.secondary.lims

/**
 * Performs a stress test of the LIMS import and alias services
 *
 * WIP: will remove this comment when done.
 *
 * Designed to be helpful for the following use cases.
 *
 * - Spec that verifies importing multiple lims.yml and making multiple aliases
 * - Performance tuning of a disk-backed DB
 *   - avg/min/max time per data creation (aka are INSERTS and indexing fast enough?)
 *   - avg/min/max per query for all queries (aka are SELECTS and JOINS fast enough?)
 * - Comparing different database backends
 */
object StressTest extends App {
  println("Stress test done!")


  /**
   * Creates a mock lims.yml file, allowing override of all values
   *
   * The history and semantics of all of these was unknown to @jfalkner. We'll have to fill them in
   * and enforce constraints in a later iteration.
   *
   * @param expcode
   * @param runcode
   * @param path
   * @param user
   * @param uuid
   * @param tracefile
   * @param desc
   * @param well
   * @param cellbarcode
   * @param seqkitbarcode
   * @param cellindex
   * @param colnum
   * @param samplename
   * @param instid
   * @return
   */
  def mockLimsYmlContent(
      // taken from `cat /net/pbi/collections/322/3220001/r54003_20160212_165105/1_A01/lims.yml`
      expcode: Int = 3220001,
      runcode: String = "3220001-0006",
      path: String = "file:///pbi/collections/322/3220001/r54003_20160212_165105/1_A01",
      user: String = "MilhouseUser",
      uuid: String = "1695780a2e7a0bb7cb1e186a3ee01deb",
      tracefile: String = "m54003_160212_165114.trc.h5",
      desc: String = "TestSample",
      well: String = "A01",
      cellbarcode: String = "00000133635908926745416610",
      seqkitbarcode: String = "002222100620000123119",
      cellindex: Int = 0,
      colnum: Int = 0,
      samplename: String = "TestSample",
      instid: Int = 90): String = {
    s"""expcode: $expcode
        |runcode: '$runcode'
        |path: '$path'
        |user: '$user'
        |uid: '$uuid'
        |tracefile: '$tracefile'
        |description: '$desc'
        |wellname: '$well'
        |cellbarcode: '$cellbarcode'
        |seqkitbarcode: '$seqkitbarcode'
        |cellindex: $cellindex
        |colnum: $colnum
        |samplename: '$samplename'
        |instid: $instid""".stripMargin
  }
}
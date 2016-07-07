package com.pacbio.secondary.lims


/**
 * Superset of a SubreadDataSet file that represents lims.yml
 *
 * This is a practical superset of the subreaddataset XML file data. It includes various information that gets
 * calculated and included in the lims.yml files MJ makes. Long-term, this abstraction needs to be rethought and likely
 * recast to a more formal abstraction. As-is, this data duplicates other
 */
case class LimsYml( // TODO: rename to LimsSubreadDataSet?
    expcode: Int,
    runcode: String,
    path: String,
    user: String,
    uid: String,
    tracefile: String,
    description: String,
    wellname: String,
    cellbarcode: String,
    seqkitbarcode: String,
    cellindex: Int,
    colnum: Int,
    samplename: String,
    instid: Int
)
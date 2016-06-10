// FIXME there is an appalling amount of code duplication here

package com.pacbio.secondary.analysis.datasets

import java.util.UUID

import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.PacBioFileWriter
import com.pacbio.secondary.analysis.legacy.ReferenceContig

import java.nio.file.{Paths, Path}
import org.joda.time.{DateTime => JodaDateTime}

/*
 * Reference Dataset
 */

// This is an abstraction representation and has nothing to do
// with the file source (i.e., IO layers)
case class GmapReferenceDataset(
    organism: String,
    ploidy: String,
    contigs: Seq[ReferenceContig],
    metadata: DataSetMetaData)

case class GmapReferenceDatasetIO(
    fastaFile: String,
    dataset: GmapReferenceDataset,
    gmapDbFile: String)

// The path of the actual dataset file isn't necessarily known when the object is created
// Need to better define the gmapDbFile absolute path or relative path consistency
// Not thrilled about this
case class GmapReferenceDatasetFileIO(
    path: Path,
    fastaFile: String,
    dataset: GmapReferenceDataset,
    gmapDbFile: String)


trait GmapReferenceDataSetWriter extends PacBioFileWriter[GmapReferenceDatasetIO] {

  /**
   * Write the GmapReferenceSet XML. If the path provided is relative to the index files, the files will be
   * written as relative paths, otherwise they'll be written as absolute paths. This keeps the reference self-contained
   * and move-able.
   *
   * FIXME. This ******NEEDS******* to be converted to use the jaxb API for consistency
   *
   * @param ds
   * @param path
   * @return
   */
  def writeFile(ds: GmapReferenceDatasetIO, path: Path): Path = {

    def toC(c: ReferenceContig) = <pbbase:Contig Name={c.name} Description={c.name} Length={c.length.toString} Digest={c.md5}/>
    val createdAt = JodaDateTime.now().toString

    val s = {
      <pbds:GmapReferenceSet
      xmlns:pbbase="http://pacificbiosciences.com/PacBioBaseDataModel.xsd"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xmlns:pbds="http://pacificbiosciences.com/PacBioDatasets.xsd"
      MetaType={FileTypes.DS_GMAP_REF.fileTypeId}
      Name={ds.dataset.metadata.name}
      Tags="gmap reference converter"
      TimeStampedName={"gmapreferenceset_" + ds.dataset.metadata.uuid.toString}
      UniqueId={ds.dataset.metadata.uuid.toString}
      Version={ds.dataset.metadata.version}
      CreatedAt={createdAt}
      Author="pbscala.tools.fasta_to_gmap_reference">
        <pbbase:ExternalResources>
          <pbbase:ExternalResource Name="PacBio Fasta Reference" Description="PacBio compliant Reference FASTA file." MetaType="PacBio.ReferenceFile.ReferenceFastaFile" ResourceId={ds.fastaFile} Tags="reference" UniqueId={UUID.randomUUID().toString}>
            <pbbase:ExternalResources>
              <pbbase:ExternalResource Name="PacBio GMAP DB" Description="Database for running GMAP" MetaType={FileTypes.JSON.fileTypeId} ResourceId={ds.gmapDbFile} Tags="gmap db" UniqueId={UUID.randomUUID().toString}/>
            </pbbase:ExternalResources>
          </pbbase:ExternalResource>
        </pbbase:ExternalResources>
        <pbds:DataSetMetadata>
          <pbds:TotalLength>
            {ds.dataset.contigs.foldLeft(0)((m, n) => m + n.length)}
          </pbds:TotalLength>
          <pbds:NumRecords>
            {ds.dataset.contigs.length}
          </pbds:NumRecords>
          <pbds:Organism>
            {ds.dataset.organism}
          </pbds:Organism>
          <pbds:Ploidy>
            {ds.dataset.ploidy}
          </pbds:Ploidy>
          <pbds:Contigs>
            {ds.dataset.contigs.map(x => toC(x))}
          </pbds:Contigs>
        </pbds:DataSetMetadata>
      </pbds:GmapReferenceSet>
    }


    scala.xml.XML.save(path.toAbsolutePath.toString, s, "UTF-8", true, null)

    // This is really what the function should return, but the base trait
    // needs to encode this.
    GmapReferenceDatasetFileIO(path, ds.fastaFile, ds.dataset, ds.gmapDbFile)
    path
  }
}


object GmapReferenceDataset extends GmapReferenceDataSetWriter

package com.pacbio.secondary.smrtlink.analysis.datasets

import java.util.UUID

import com.pacbio.secondary.smrtlink.analysis.PacBioFileWriter

import java.nio.file.{Paths, Path}
import org.joda.time.{DateTime => JodaDateTime}

/*
 * Reference Dataset
 */

case class ReferenceContig(name: String,
                           description: String,
                           length: Int,
                           md5: String)

// This is an abstraction representation and has nothing to do
// with the file source (i.e., IO layers)
case class ReferenceDataset(organism: String,
                            ploidy: String,
                            contigs: Seq[ReferenceContig],
                            metadata: DataSetMetaData)

case class ReferenceDatasetIO(fastaFile: String,
                              dataset: ReferenceDataset,
                              indexFiles: Seq[DatasetIndexFile])

// The path of the actual dataset file isn't necessarily known when the object is created
// Need to better define the indexFiles absolute path or relative path consistency
// Not thrilled about this
case class ReferenceDatasetFileIO(path: Path,
                                  fastaFile: String,
                                  dataset: ReferenceDataset,
                                  indexFiles: Seq[DatasetIndexFile])

trait ReferenceDataSetWriter extends PacBioFileWriter[ReferenceDatasetIO] {

  /**
    * Write the Reference DataSet XML. If the path provided is relative to the index files, the files will be
    * written as relative paths, otherwise they'll be written as absolute paths. This keeps the reference self-contained
    * and move-able.
    *
    * FIXME. This ******NEEDS******* to be converted to use the jaxb API for consistency
    *
    * @param ds
    * @param path
    * @return
    */
  def writeFile(ds: ReferenceDatasetIO, path: Path): Path = {

    def toC(c: ReferenceContig) =
      <pbbase:Contig Name={c.name} Description={c.name} Length={c.length.toString} Digest={c.md5}/>
    def toIndex(d: DatasetIndexFile) = {
      val u = UUID.randomUUID()
      <pbbase:FileIndex UniqueId={u.toString} TimeStampedName={"tsn_" + u.toString} MetaType={d.indexType} ResourceId={d.url}/>
    }

    val createdAt = JodaDateTime.now().toString

    val s = {
      <pbds:ReferenceSet
      xmlns:pbbase="http://pacificbiosciences.com/PacBioBaseDataModel.xsd"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xmlns:pbds="http://pacificbiosciences.com/PacBioDatasets.xsd"
      MetaType="PacBio.DataSet.ReferenceSet"
      Name={ds.dataset.metadata.name}
      Tags="reference converter"
      TimeStampedName={"referenceset_" + ds.dataset.metadata.uuid.toString}
      UniqueId={ds.dataset.metadata.uuid.toString}
      Version={ds.dataset.metadata.version}
      CreatedAt={createdAt}
      Author="pbscala.tools.fasta_to_reference_dataset">
        <pbbase:ExternalResources>
          <pbbase:ExternalResource Name="PacBio Fasta Reference" Description="PacBio compliant Reference FASTA file." MetaType="PacBio.ReferenceFile.ReferenceFastaFile" ResourceId={ds.fastaFile} Tags="reference">
            <pbbase:FileIndices>
              {ds.indexFiles.map(x => toIndex(x))}
            </pbbase:FileIndices>
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
      </pbds:ReferenceSet>
    }

    scala.xml.XML.save(path.toAbsolutePath.toString, s, "UTF-8", true, null)

    // This is really what the function should return, but the base trait
    // needs to encode this.
    ReferenceDatasetFileIO(path, ds.fastaFile, ds.dataset, ds.indexFiles)
    path
  }
}

object ReferenceDataset extends ReferenceDataSetWriter

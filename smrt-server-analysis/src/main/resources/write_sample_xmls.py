from xmlbuilder import XMLBuilder
import logging
import os
 
import fixtures_dataset_builder as B
 
log = logging.getLogger(__name__)
MOVIE_NAME = "m130110_062238_00114_c100480560100000001823075906281381_s1_p0"
 
def to_multibax_subread_ds_xml(ds, nameSuffix=""):
    x = XMLBuilder('DataSet', UniqueId=ds.id,
                   MetaType="PacBio.DataSet.SubreadSet",
                   Name="DataSet_%s" % nameSuffix,
                   Tags=" ".join(ds.tags),
                   # Description="SomeDescription", # TODO add to schema?
                   )
 
    with x.MetaData:
        x.CreatedAt(ds.createdAt)
        x.Version(ds.version)
        x.NumRecords("%i" % ds.numRecords)
        x.TotalLength("%i" % ds.totalLength)
        with x.CellIds:
            for cellId in ds.cellIds:
                # TODO Ask Elias how to reference entities in the XML?
                x.CellId(cellId) 

        x.WellSampleName(ds.wellSampleName)
        x.SampleName(ds.sampleName)
        x.PlateName(ds.plateName)
        x.InstrumentId(ds.instrumentId)
        x.InstrumentName(ds.instrumentName)
        x.RunId(ds.runId)
        x.RunName(ds.runName)
        x.CollectionProtocol(ds.collectionProtocol)
        x.PrimaryVersion(ds.primaryVersion)
        x.PrimaryProtocol(ds.primaryProtocol)
        x.Chemistry(ds.chemistry)

    with x.ExternalDataReferences:
        # TODO add better paths?
        with x.InputOutputData(path="/first/path/to/2.bax.h5"):
            with x.MetaData:
                x.RunName(ds.runName)
        with x.InputOutputData(path="/second/path/to/1.bax.h5"):
            with x.MetaData:
                x.RunName(ds.runName)

    with x.SubsetDefinition(MetaType="Subset.ReadSubset.ByZmwList"):
        with x.Movie(id=MOVIE_NAME):
            x.Zmw("1")
            x.Zmw("2")
        
    with x.SubsetLabel(MetaType="SubsetLabel.ReadSubsetLabel.ByZmwList",
                       Name="First ZMW Label"):
        with x.Movie(id=MOVIE_NAME):
            x.Zmw("1")
        
    return x
 
 
def output_subread_examples(outdir="examples", num_examples=1):
    json_schema = 'dataset_subread_schema.json'
    def new_xml_example(i):
        return "%s/SubreadSet_%i.xml" % (outdir, i)
    for idx in range(num_examples):
        dataset = B.builder(json_schema, 'SubreadDataSet')
        with open(new_xml_example(idx), "w") as fh:
            xml = to_multibax_subread_ds_xml(dataset, 
                                             nameSuffix="SubreadSet_%i" % idx)
            fh.write(str(xml))

if __name__=="__main__":
    # Create subread dataset examples
    outdir="subreadset_examples"
    if not os.path.isdir(outdir): 
        os.mkdir(outdir)
    output_subread_examples(outdir=outdir, num_examples=50)

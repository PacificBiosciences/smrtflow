# Internal Doc for Simulator Scenarios

This provides a high level summary of each Simulator Scenario. Any Mocked out component(s) should be clearly enumeration. 

Each Scenario should have of a clear description for setup and running the scenario.

- All (?) Scenarios require *smrtflow.server.host* and *smrtflow.server.port* as config params.
- Each Test requires(?) PacBioTestData to be defined (*smrtflow.tests.test-files* or *PB_TEST_DATA_FILES* ENV var.)

Any Mocked


## PbsmrtpipeScenario

Runs a "pbsmrtpipe" Job type from the Services

Inputs: Only SL host and port 

Requires: SL Instance, SL Tools

1. Runs Import DataSet job(s) for both the SubreadSet and lambdaNeb ReferenceSet from PacBioTestFiles
2. Runs an analysis (i.e., pbsmrtpipe) job using the "pbsmrtpipe.pipelines.dev_diagnostic" pipeline
3. Validates Job results (datastore, reports)


## Run Design With ICS

Inputs: TODO

Requires: TODO

1. Create a Run in SL (Clearly define this)
2. Trigger ICS (which will run the PA sim). ICS "completes" but will not import a SubreadSet into SL (this is different from the production system)
3. Take a SubreadSet from PacBioTestData files that has a valid (and mappable) SubreadSet and reference. Update the UUIDs to be consistent with the Run SubreadSet UUID.
4. Run pbvalidate on the SubreadSet (SL Tools, specifically the pbcoretools repo that has the pbvalidate exe https://github.com/PacificBiosciences/pbcoretools) 
5. Import SubreadSet into SL 
6. Verify RunQC (verify that import-dataset job for the SubreadSet has the expected Report Metrics)
7. Create SL SAT job using pipeline id "pbsmrtpipe.pipelines.sa3_sat" with the reference from PacBioDataSet and the SubreadSet that was imported
8. Verify Successful job and Reports are present
Note that #3 can be swapped out in a future iteration for PA generated BAM files. From an interface standpoint, as long as PA is generating a valid SubreadSet file that passes pbvalidate, it shouldn't matter. Using the PA generate file will required changes to SAT to make sure that non-mappable data can successfully get though the SAT pipeline (Hence the motivation for using PacBioTestData files datasets).


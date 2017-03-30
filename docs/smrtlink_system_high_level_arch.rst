SMRT Link System Architecture High Level Overview
=================================================

The SMRT Link System is comprised of 6 core components:

-  *SMRT Link System* Installer for general admin, configuring the
   system, and upgrading
-  *SMRT Link Tools* Commandline tools written in python, c++ and scala
   from the SAT and SL team
-  *SMRT Link Analysis Services* (SLA) Scala driven webservices using
   `spray framework <http://spray.io/>`__
-  *SMRT Link Tomcat WebServer* For SMRT Link UI written in
   Javascript/Typescript using angular2
-  *SMRT View* Visualization of *SMRT Link Analysis Jobs*
-  Enterprise *WSO2 API Manager* for authentication and authorization

Note, "SMRT Link" is a very overloaded term. It's recommended to
communicate using the subcomponent of the system to avoid confusion.

This overview provides a description of the core abstractions used in
the SMRT Link Analysis Services to process and produce data leverage
**SMRT Link Tools**. The core unit of computational work at the SMRT
Link Analysis Services level is the **ServiceJob**.

ServiceJob
~~~~~~~~~~

A ServiceJob (i.e., "engine" job refered to in the scala code) is a
general polymorphic async computational unit that takes input of type
*T* and returns a *DataStore*. A DataStore is a list of *DataStoreFile*
instances. Each *DataStoreFile* contain metadata about the file, such as
file type (GFF, Fasta, PacBio DataSet, PacBio Report, Log, Txt),
globally unique id (uuid), file size, and "source id" (details provided
in a later section).

After a *ServiceJob* is run, the *DataStore* (and it's
*DataStoreFile(s)*) is imported back into SMRT Link Analysis. These
DataSets are now accessible for further analysis by other ServiceJob(s).

(In psuedo-ish scala code)

.. code:: scala

    def run[T](opts: T): DataStore

There are several Service Job types within SMRT Link Analysis Services
of note:

Import DataSet
^^^^^^^^^^^^^^

Takes a path to a PacBio DataSet and generates a DataStore with a path
to the PacBio DataSet as well as generating Report(s) file types and a
Log of the ServiceJob output.

Fasta Reference Convert and Import
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Task a path to a fasta file and converts to a PacBio ReferenceSet. The
ReferenceSet (and DataSet Reports, Log of ServiceJob) are added to the
DataStore

"Analysis" or "pbsmrtpipe" Job
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Internally, this job type is referred to as "pbsmrtpipe" job, whereas
marketing refers to this job type as "analysis". This is what will be
displayed in the SMRT Link UI.

This job type takes a Map[String, EntryPoint] (**EntryPoint** defined
below), task options, pipeine template id as inputs (i.e., "T") and
emits a DataStore. Depending on the pipeline template Id, the DataStore
will be populated with different output file types. (Pipeline Templates
are described in more detail in the next section).

In pseuod-ish scala code:

.. code:: scala

    case class Opts(
       entyPoints:Map[String, EntryPoint], 
       taskOptions: Map[String, TaskOption], 
       pipelineId: String, 
       jobName: String)

    def run[Opts](opts: Opt): DataStore

Analysis jobs are the heart of processing PacBio DataSets (e.g.,
SubreadSet(s)) within SMRT Link.

An **EntryPoint** is a container for an *id* of DataSet and a
DataSetMetaType (e.g, "SubreadSet", "ReferenceSet"). The SLA Services
will resolve the DataSet to a path that can be used within a pbsmrtpipe
execution.

Each analysis pipeline id has a well-defined set of EntryPoint(s) that
are required. For example a pipeline template id "alpha" might have an
entry point of *e\_subread:SubreadSet* and *e\_rset:ReferenceSet* (using
the *entry-id:DataSetMetaType* notation).

A *Pipeline Template* is a static encoding of the *EntryPoint(s)* of a
pipeline (by id), default task options and display metadata, such as
name, description of the pipeline. *Pipeline Template objects* are
currently defined in python (as code to enable resuability of
subworkflows) and can be emitted as JSON files. These JSON files are
loaded by *SMRT Link Analysis* on startup and exposed as webservice (for
the UI or *pbservice*).

The schema for the **Pipeline Template** data model is
`here <https://github.com/PacificBiosciences/pbsmrtpipe/blob/master/pbsmrtpipe/schemas/pipeline_template.avsc>`__

Pipelines are executed by *pbsmrtpipe* which will call one (or more)
tasks defined using the PacBio **ToolContract** interface.

The **ToolContract interface** encodes task metadata, such as the input
and output file types (e.g, GFF, SubreadSet), available and default task
options, is the task distributed, number of processors/threads to use,
etc...

Note, for historical reasons, there's some loosenses in nomenclature;
"task" and "tool contract" are often used interchangeably. These
represent the same noun in the SMRT Link software stack.

More details of ToolContract data model and interface is defined in
`pbcommand <http://pbcommand.readthedocs.io/en/latest/commandline_interface.html#details-of-tool-contract>`__

More details of pbsmrtpipe and Creating Analysis Pipelines are described
`here <http://pbsmrtpipe.readthedocs.io/>`__.

**By design**, any pipeline that is runnable from the SMRT Link Services
can be runnable directly from the commandline by invoking **pbsmrtpipe**
directly. Conversely, only a subset of Pipelines that are runnable from
the commandline are runnable from SMRT Link Services. Specifically,
**only pipelines that only have PacBio DataSet types as EntryPoints are
supported**. This is because the UI only allows selecting and binding of
**EntryPoint(s)** as PacBio DataSets.

There is a "raw" pbsmrtpipe interface to the SMRT Link Web services that
supports creating ServiceJobs that already have the EntryPoint(s)
resolved to paths.

Other Job Types Examples
^^^^^^^^^^^^^^^^^^^^^^^^

While the previous example of *ServiceJob*\ (s) are focused on importing
or analysis to creating output files, there are other uses for a
ServiceJob. For example, the *DeleteDataSetJob* is a job type that will
delete datasets (and parent datasets) from the file system
asynchronously and generate a DataStore file with a Report and Log of
the output.

Note that only "pbsmrtpipe" (i.e., analysis) and import-dataset Jobs (in
DataManagement) are displayed in SMRT Link UI.

ServiceJob Data Model and Polymorphism
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The metadata of a **ServiceJob** is stored within the SMRT Link Database
and is the core unit that is displayed on the UI.

For brevity, *only a subset* of the properties are show below. See the
SMRT Link docs for more details.

.. code:: scala

    case class ServiceJob(
      uuid: UUID, 
      id: String, 
      name: String, 
      jobTypeId: String,
      state: JobStates.JobState, 
      createdAt: DateTime, 
      settings: JsonObject)

Property Summary

-  *UUID* globally unique identifer for the job
-  *id* unique to the SMRT Link Instance
-  *jobTypeId* Unique identifier for the job type (e.g., "pbsmrtpipe")
-  *name* Name of the ServiceJob
-  *state* Current state of the job
-  *settings* Json structure of the JobType specific settings

The **settings** are where the polymorophism has handled.

For example an *import-dataset* job will have **settings** of:

.. code:: javascript

    {"path": "/path/to/subreadset.xml", "datasetMetaType": "PacBio.MetaTypes.SubreadSet"}

Whereas "analysis" jobs will have the pipeline id, entry points
(excluded for brevity) amongst other options that are encoding type *T*
for the *ServiceJob* options.

.. code:: javascript

    {"pipelineId": "pbsmrtpipe.pipelines.my_pipeline"}

In summary, given a **ServiceJob**, the **settings** is a well-defined
schema for the specific **jobTypeId**.

Model for Running Service Jobs within SMRT Link
-----------------------------------------------

Internal to the SMRT Link Services is an execution manager leveraging
the `**akka framework** <http://akka.io/>`__. This enables the number of
*ServiceJob(s)* running to be throttled and to not overload the box
where the services are running.

For example, if you submit 100 analysis jobs, you won't be forking and
creating 100 pbsmrtpipe instances that are submitting N number of tasks
to the cluster manager. The max number of ServiceJob(s) that are running
will be throttled by the value of max number of service workers that is
defined in the SMRT Link System (JSON) config.

See the docs for more details on the configuration.

DataStore
~~~~~~~~~

As described in the previous section, a **ServiceJob** outputs a
*DataStore*. A *DataStore* is a list of *DataStoreFile* instances that
contain metadata about the file, such as file type (GFF, Fasta, PacBio
DataSet, PacBio Report, Log, Txt), globally unique id (uuid), file size,
and "source id".

Each *DataStoreFile* has a "source id" that is unique to the Job type
and can be understood as mechanism to reference a specific output from a
*ServiceJob*.

**This provides an identifier to refer to the output of pipeline of a
specific pipeine id.**

DataStoreFile example

.. code:: javascript

    {
    "modifiedAt": "2017-03-03T11:52:21.031Z",
    "name": "Filtered SubreadSet XML",
    "fileTypeId": "PacBio.DataSet.SubreadSet",
    "path": "/path/to/pbcoretools.tasks.filterdataset-0/filtered.subreadset.xml",
    "description": "Filtered SubreadSet XML",
    "uuid": "f5166313-f3e4-a963-a230-2b551666b30b",
    "fileSize": 8912,
    "importedAt": "2017-03-03T11:52:21.031Z",
    "jobId": 279,
    "createdAt": "2017-03-03T11:52:21.031Z",
    "isActive": true,
    "jobUUID": "a45451da-3f2f-4e8e-9f76-61a12a306936",
    "sourceId": "pbcoretools.tasks.filterdataset-out-0"
    }

SMRT Link Importing of DataStoreFile(s) from a DataStore
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

As a ServiceJob is run *DataStoreFile(s)* are being generated and
imported into the SMRT Link System. For example, after mapping is
completed in a Resequencing job, the *AlignmentSet* will be imported
back into the system can be used in other pipelines.

Depending on the *fileTypeId* of the *DataStoreFile*, the import might
trigger other actions and store a richer set of metadata into the SMRT
Link Database.

The **two specific file types** are **PacBio Report** and **PacBio
DataSet(s)**, such as BarcodeSet, SubreadSet, ReferenceSet.

PacBio DataSet Overview
^^^^^^^^^^^^^^^^^^^^^^^

These XML files are a metadata wrapper to underlying file, or files,
such as fasta files, gmap indexes, or aligned or un-aligned BAM files.

Please see the official docs
`here <http://pacbiofileformats.readthedocs.io/en/3.0/DataSet.html>`__

SMRT Link Analysis supports *all* PacBio DataSet types.

PacBio Report Overview
^^^^^^^^^^^^^^^^^^^^^^

The PacBio Report data model is used to encode the *metrics* computed
(e.g, max readlength), plot, plot groups and tables. Each report has a
UUID that is globally unique and an "id" to communicate the report type
(e.g., "mapping\_stats")

Currently, there are officially supported APIs to read and write (via
JSON) these data models. The supported models are in python
(`pbcommand <http://pbcommand.readthedocs.io/en/latest/report_model.html>`__)
and in scala
(`smrtflow <https://github.com/PacificBiosciences/smrtflow>`__)

The Report DataModel `avro Schema is
here <https://github.com/PacificBiosciences/pbcommand/blob/master/pbcommand/schemas/pbreport.avsc>`__

Many (almost all) *Report(s)* generated from ServiceJob(s) are from the
python `pbreports <https://github.com/PacificBiosciences/pbreports/>`__
package. By default, the (minimal) display data in the report will be
used to display the *Report* in the SMRT Link UI.

Each Report type (by id) has a schema of the expected output types and
attempts to separate the view data from the model. This abstraction is a
`**Report
Spec** <https://github.com/PacificBiosciences/pbreports/tree/master/pbreports/report/specs>`__.

Further customization of the view of a *Report* by type can be
configured using **ReportViewRules** and loaded by *SMRT Link Analysis*
on start up.

Accessing Report(s) from SMRT Link Analysis
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The raw Reports (as JSON) are accesible from the SMRT Link Services as
follows.

Get a List of all datastore files.

::

    /secondary-analysis/jobs/pbsmrtpipe/1234/datastore

To display only the Report file types, ServiceReportFile (similar to the
DataStoreFile)

::

    /secondary-analysis/jobs/pbsmrtpipe/1234/reports

From the report UUID referenced in the ServiceReportFile, the raw JSON
of the report can be obtained.

::

    /secondary-analysis/jobs/pbsmrtpipe/1234/reports/{UUID}

See the SMRT Link Analysis Service swagger docs for more details.

Configuring SMRT Link
---------------------

SMRT Link Analysis, Tomcat webserver, SMRT View and WSO2 are configured
using the **smrtlink-system-config.json** file within the SMRT Link
Analysi GUI Bundle. This is located
``smrtsuite/current/bundles/smrtlink-analysisservices-gui`` in the SL
System build.

The config file uses the scala/java HOCON (as JSON) format. The `Schema
for the config is
here <https://github.com/PacificBiosciences/smrtflow/blob/master/SmrtLinkSystemConfig.avsc>`__

Interacting With SMRT Link Analysis Services APIs
-------------------------------------------------

The recommended model for interfacing with the SMRT Link Services is
using **pbservice** commandline exe, or the `scala client API in
smrtflow <https://github.com/PacificBiosciences/smrtflow>`__

The rich comandline tool, **pbservice** provides access to get job
status of SMRT Link Analysis jobs, submit analysis jobs, import datasets
and much more.

Please see the
`docs <http://smrtflow.readthedocs.io/en/latest/tools.html>`__ for more
details.

**F.A.Q.** What is the difference between
`**smrtflow** <https://github.com/PacificBiosciences/smrtflow>`__ and
**SMRT Link**. SMRT Link Services and serveral commandline tools, such
as **pbservice** are written in scala. These tools and services reside
in a scala package called **smrtflow**. One of the applications in
**smrtflow** is the SMRT Link Analysis web services.

There is `python API in pbcommand to interface with the SMRT Link
Services <http://pbcommand.readthedocs.io/en/latest/pbcommand.services.html>`__
and an example ipython notebook written as a `cookbook that can be used
to demonstrate how to use the
API <http://pbcommand.readthedocs.io/en/latest/cookbook_services.html>`__.

SMRT Link Testing
~~~~~~~~~~~~~~~~~

[TBD]

-  Describe the testkit Sim layer in smrtflow for testing service driven
   pipelines
-  Describe pbtestkit for pbsmrtpipe
-  Describe SL UI tests driven by protractor

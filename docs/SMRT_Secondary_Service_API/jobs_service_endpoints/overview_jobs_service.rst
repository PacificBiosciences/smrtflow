Overview of Jobs Service
========================

Use Cases
---------

The Jobs Service enables creating and viewing jobs, as well as retrieving
job datastores, reports and events.

Key Concepts
------------

Jobs may be categorized as *Dataset Manipulation jobs* and *Analysis jobs*.

**Dataset Manipulation jobs** enable dataset creation through:

-  importing a dataset from a file,

-  merging two datasets of the same type,

-  converting HDF5 file into a dataset,

-  converting reference FASTA file into a dataset,

etc.

**Analysis job** is defined as a set of the following information:

-  An analysis *workflow template* (e.g. resequencing) that refers to a
   series of *tasks* necessary to perform the analysis;

-  Analysis *options* and *parameters*;

-  One or more input *datasets*;

-  Optional analysis *metadata*, such as a name, comments, owner,
   permissions. etc.

Job Types
~~~~~~~~~

Job Types list:

-  import-dataset - import a Pacbio DataSet XML file

-  import-datastore - import a PacBio DataStore JSON file

-  merge-datasets - merge PacBio XML DataSets of the same type

-  convert-rs-movie - import RS metadata.xml and create an HdfSubread
   DataSet XML file

-  convert-fasta-reference - import FASTA reference file and create a
   Reference DataSet XML file

-  pbsmrtpipe - run a Secondary Analysis pbsmrtpipe job

.. note:: Two more job types returned by :doc:`list_all_job_types` request -
   'simple' and 'mock-pbsmrtpipe' - are not in scope of this documentation,
   because they are used for development/debugging/testing purposes only.

Job States
~~~~~~~~~~

Job States list:

-  CREATED - job resource has been created

-  SUBMITTED - job has been submitted to compute resource

-  RUNNING - job is currently executing

-  SUCCESSFUL - job has successfully completed execution

-  FAILED - job has one or more failed tasks

-  KILLED - job is forcefully killed

-  STOPPED - no more tasks will be submitted, but currently running
   tasks will be allowed to completed


Tools
=====


Commandline Tools



Commandline Interaction with SMRT Link Services
-----------------------------------------------

The `pbservice` exe can be used in to interact with the SMRT Link Services from the commandline.  Note that machine-readable output can be obtained in most
cases by adding the argument `--json` to the command line; attempting to parse
plain-text output is strongly discouraged.


Checking the Status
^^^^^^^^^^^^^^^^^^^

.. code-block:: bash

    $> pbservice status --host smrtlink-beta --port 8081


.. note:: PB_SERVICE_HOST and PB_SERVICE_PORT env variables can be used to set the default `--host` and `--port` values.


.. code-block:: bash

    $> export PB_SERVICE_PORT=8081
    $> export PB_SERVICE_HOST=smrtlink-bihourly
    $> pbservice status
       SMRTLink Services Version: 0.1.10-c63303e
       Status: Services have been up for 27 minutes and 13.320 seconds.
       DataSet Summary:
       SubreadSets 22
       HdfSubreadSets 56
       ReferenceSets 9
       BarcodeSets 7
       AlignmentSets 10
       ConsensusReadSets 6
       ConsensusAlignmentSets 1
       ContigSets 9
       GmapReferenceSets 0
       SMRT Link Job Summary:
       import-dataset Jobs 35
       merge-dataset Jobs 0
       convert-fasta-reference Jobs 2
       pbsmrtpipe Jobs 16


Importing a DataSet into SMRT Link Server
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: bash

    $> pbservice import-dataset --host smrtlink-beta --port 8081 /path/to/subreadset.xml


.. note:: This can also operate recursively on a directory. All files ending in `*.subreadset.xml` will be imported into the system. Any files that have already been imported into the system will be skipped.


Submit an Resequencing Analysis Job
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: bash

    $> pbservice run-pipeline sa3_ds_resequencing_fat --host smrtlink-beta --port 8081 \
        -e /path/to/subreadset.xml \
        -e /path/to/referenceset.xml \
        --job-title my_job_title

This will automatically import the entry point datasets if they are not already
present in the system.


Resubmitting an Analysis Job
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: bash

    $> pbservice get-job <ID> --show-settings --host smrtlink-beta --port 8081 > my_job.json
    $> pbservice run-analysis my_job.json --host smrtlink-beta --port 8081 --timeout 36000


You may edit the JSON file in between these commands if you want to modify the settings. It's recommended that you change the job name or add a suffix of "_resubmit".


Importing/Converting a RSII movie into SMRT Link
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: bash

    $> pbservice import-rs-movie --host smrtlink-alpha --port 8081 /path/to/movies

This will create a new HdfSubreadSet XML and database entry.  Note that the
underlying HDF5 data files will not be converted at this stage; conversion to
BAM format requires running a separate pbsmrtpipe job.


Querying Job History
^^^^^^^^^^^^^^^^^^^^

.. code-block:: bash

    $> pbservice get-jobs --job-state FAILED --job-type pbsmrtpipe --search-name like:hg19 --search-pipeline pbsmrtpipe.pipelines.sa3_ds_resequencing_fat

The `get-jobs` subcommand allows searching for jobs by type, name (full or
partial), job state, and/or pbsmrtpipe pipeline (if relevant).


For further options, please use `pbservice --help` for more functionality.


Conversion and Other Tools
--------------------------

Fasta to ReferenceSet
^^^^^^^^^^^^^^^^^^^^^

Convert a Fasta file to a ReferenceSet and generate the required index files (fai and sa (*requires* `sawriter` exe in $PATH)).


.. code-block:: bash

    $> fasta-to-reference /path/to/file.fasta /path/to/output-dir my-reference-name --organism=my-org --ploidy=haploid


Convert RSII movie metadata XML to HdfSubreadSet XML
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: bash

    $> movie-metadata-to-dataset /path/to/rs-movie.metadata.xml /path/to/output.subreadset.xml



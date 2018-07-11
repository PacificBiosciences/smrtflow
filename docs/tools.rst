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


Authentication
^^^^^^^^^^^^^^

Use of pbservice to access a remote SMRT Link server (not running on localhost)
requires user authentication over HTTPS; this is also required for some API
calls that only work with authentication (projects are the most important such
feature).  There are several ways to specify authentication credentials:

1) Add `--ask-pass` to the command-line arguments, and pbservice will prompt
   for a password.  This is recommended for interactive use since the password
   stays private.  If your Unix login ID is different from the user ID you
   wish to log in to SMRT Link with, you also need to add `--user <username>`.
2) Add `--user <username> --password <password>` to the command line
   arguments.  **Because this will display the password in shell history and/or
   log files, you should never do this with a full Unix account.**  Users that
   need this form (e.g. for scripting) should obtain SMRT Link login
   credentials that do not provide access to any other systems.
3) Set the environment variables `PB_SERVICE_AUTH_USER` and
   `PB_SERVICE_AUTH_PASSWORD`.  Again, this should never be done with a Unix
   account, only a limited SMRT Link-specific account.


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


Accept SMRT Link User Agreement
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

To accept the SMRT Link user agreement from the commandline, use the `accept-user-agreement` tool in the SMRT Link install. This enables commandline use to accept the EULA as well as editing the configuration.

.. code-block:: bash

    $> accept-user-agreement  --port 8081 --host localhost
        Accepted Eula EulaRecord(admin,2018-07-11T13:05:19.147Z,6.0.0.SNAPSHOT40824,Linux version 3.13.0-33-generic (buildd@tipua) (gcc version 4.8.2 (Ubuntu 4.8.2-19ubuntu1) ) #58-Ubuntu SMP Tue Jul 29 16:45:05 UTC 2014,true,true)
        Successfully completed running pbscala.tools.accept_user_agreement 0.2.0 (smrtflow 0.17.1+40823.c584820) in 3 sec.

Use '--help' for more information about configuration SMRT Link install and Job metrics.

To Update the configuration of the Eula, get access the current accepted EULA.

.. code-block:: bash

    $> ~$ curl -XGET http://localhost:8081/smrt-link/eula | python -m json.tool
    [
        {
            "acceptedAt": "2018-07-11T13:05:19.147Z",
            "enableInstallMetrics": true,
            "enableJobMetrics": false,
            "osVersion": "Linux version 3.13.0-33-generic (buildd@tipua) (gcc version 4.8.2 (Ubuntu 4.8.2-19ubuntu1) ) #58-Ubuntu SMP Tue Jul 29 16:45:05 UTC 2014\n",
            "smrtlinkVersion": "6.0.0.SNAPSHOT40824",
            "user": "admin"
        }
    ]

Run the '--update true' option with the necessary metrics flags (note, both flags must be provided). To disable sending SMRT Link job data to PacBio, set '--job-metrics false'.

.. code-block:: bash

    $ accept-user-agreement --update true --install-metrics true --job-metrics true  --host localhost --port 8081 --log2stdout
        2018-07-11 22:34:39.293UTC INFO [main] c.p.s.s.t.AcceptUserAgreement$ - Starting to run tool pbscala.tools.accept_user_agreement with smrtflow 0.17.1+40823.c584820
        2018-07-11 22:34:39.301UTC INFO [main] c.p.s.s.t.AcceptUserAgreement$ - Successfully Parsed options AcceptUserAgreementConfig(localhost,8081,mkocher,true,true,true)
        2018-07-11 22:34:39.743UTC INFO [accept-eula-akka.actor.default-dispatcher-2] a.e.s.Slf4jLogger - Slf4jLogger started
        Updated Eula EulaRecord(admin,2018-07-11T13:05:19.147Z,6.0.0.SNAPSHOT40824,Linux version 3.13.0-33-generic (buildd@tipua) (gcc version 4.8.2 (Ubuntu 4.8.2-19ubuntu1) ) #58-Ubuntu SMP Tue Jul 29 16:45:05 UTC 2014,true,true)
        Successfully completed running pbscala.tools.accept_user_agreement 0.2.0 (smrtflow 0.17.1+40823.c584820) in 5 sec.
        2018-07-11 22:34:43.131UTC INFO [main] c.p.s.s.t.AcceptUserAgreement$ - Updated Eula EulaRecord(admin,2018-07-11T13:05:19.147Z,6.0.0.SNAPSHOT40824,Linux version 3.13.0-33-generic (buildd@tipua) (gcc version 4.8.2 (Ubuntu 4.8.2-19ubuntu1) ) #58-Ubuntu SMP Tue Jul 29 16:45:05 UTC 2014,true,true)
        2018-07-11 22:34:43.131UTC INFO [main] c.p.s.s.t.AcceptUserAgreement$ - Successfully completed running pbscala.tools.accept_user_agreement 0.2.0 (smrtflow 0.17.1+40823.c584820) in 5 sec.


.. code-block:: bash

    $ curl -XGET http://localhost:8081/smrt-link/eula | python -m json.tool
      % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
        100   303  100   303    0     0  50898      0 --:--:-- --:--:-- --:--:-- 60600
    [
        {
            "acceptedAt": "2018-07-11T13:05:19.147Z",
            "enableInstallMetrics": true,
            "enableJobMetrics": true,
            "osVersion": "Linux version 3.13.0-33-generic (buildd@tipua) (gcc version 4.8.2 (Ubuntu 4.8.2-19ubuntu1) ) #58-Ubuntu SMP Tue Jul 29 16:45:05 UTC 2014\n",
            "smrtlinkVersion": "6.0.0.SNAPSHOT40824",
            "user": "admin"
        }
    ]


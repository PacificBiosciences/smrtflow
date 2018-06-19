SMRT Link Services Common Tasks And Workflows
=============================================

This chapter describes common tasks performed using the SMRT Link
Web Services API and provides “how to” recipes for accomplishing
these tasks.  To accomplish a task, you usually need to perform several API
calls; the workflow describes the order of these calls.

As an example of a real-world workflow, most of the examples below roughly
correspond to what happens internally when a Site Acceptance Test is run on
the Sequel instrument and SMRT Link, starting from run design and finishing
with the analysis pipeline.


How to setup a Run in Run Design
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To setup a Run design, perform the following steps:

1. Prepare the Run Design information in an XML file. (The XML file should correspond to the PacBioDataModel.xsd schema.)

2. Create the Run design: Use the POST request with the following endpoint:

.. code-block:: bash

    POST /smrt-link/runs

The payload (request body) for this POST request is a JSON with the following fields:

-  dataModel: The serialized XML containing the Run Design information
-  name: The name of the run
-  summary: A short description of the run

Example, Create a Run design using the following API call:


.. code-block:: bash

    POST /smrt-link/runs

Use the payload as in the following example:

.. code-block:: javascript

    {
        "dataModel" : "<serialized Run Design XML file according to the PacBioDataModel.xsd schema>",
        "name" : "54999_SAT",
        "summary" : "SAT"
    }


How to monitor progress of a SMRT Link Run
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Run progress can be monitored by looking at the completion status of
each Collection associated with that run. Perform the following
steps:

1. If you do not have the Run UUID, retrieve it as follows. Get the list of all Runs, using the GET request with the following endpoint:

.. code-block:: bash

    GET /smrt-link/runs

In the response, perform a text search for the Run Name. Find the object whose "name" field is equal to the Run Name, and get the Run UUID, which can be found in the "uniqueId" field.

2. Once you have the Run UUID, get all Collections that belong to the run.

Use the Run UUID in the GET request with the following endpoint:

.. code-block:: bash

    GET /smrt-link/runs/{runUUID}/collections

The response will contain the list of all Collections of that run.

3. Monitor Collection status to see when all Collections are complete.

Until all Collections of the Run have the field "status" set to "Complete", repeat the GET request with the following endpoint:

.. code-block:: bash

    GET /smrt-link/runs/{runUUID}/collections

You may also monitor each Collection individually.

Use the Collection UUID in the GET request with the following endpoint:

.. code-block:: bash

    GET /smrt-link/runs/{runUUID}/collections/{collectionUUID}

4. To monitor Run progress using QC metrics as well, do this at the Collection level, for each Collection that belongs to this run. For instructions, see `How to get QC reports for a particular Collection`__.

The full set of QC metrics for a Collection will **only** be
available when the Collection is **complete**. Monitor the
completion status of each Collection and, for each complete
Collection, check its QC metrics. QC metrics of all Collections that
belong to the Run will let you evaluate an overall success of the
run.

Example

If you want to monitor the Run with Name = “54999_DryRun_2Cells_20161219”, use the following steps:

1. Get the list of all Runs:

.. code-block:: bash

    GET /smrt-link/runs

The response will be an array of Run objects, as in the following example (some fields are removed for display purposes)

.. code-block:: javascript

    [
        {
            “name” : “54999_SAT",
            “uniqueId” : “a836efbc-fd58-40f6-b586-43c743730fe0",
            “createdAt” : “2016-11-08T17:50:57.955Z”,
            “summary” : "SAT run”
        },
        {
            “name” : “54999_ecoli_15k",
            “uniqueId” : “798ff161-23ee-433a-bfd9-be8361b40f15”,
            “createdAt” : “2016-12-19T16:08:41.610Z”,
            “summary” : “E. coli assembly”
        },
        {
            “name” : “54999_hla_amplicons",
            “uniqueId” : “5026afad-fbfa-407a-924b-f89dd019ca9f”,
            “createdAt” : “2017-01-21T00:21:52.534Z”,
            “summary” : “Human HLA”
        }
    ]

2. Search the above response for the object with the "name" field equal to "54999_SAT".

From the above example, you will get the Run object with the "uniqueId" field equal to "a836efbc-fd58-40f6-b586-43c743730fe0".

3. With this Run UUID = a836efbc-fd58-40f6-b586-43c743730fe0, get all Collections that belong to this run:

.. code-block::

    GET /smrt-link/runs/a836efbc-fd58-40f6-b586-43c743730fe0/collections

The response will be an array of Collection objects of this run, as in
the following example:


.. code-block:: javascript

    [{
        "name" : "54999_SAT_1stCell",
        "instrumentName" : "Sequel",
        "context" : "m54999_161219_161247",
        "well" : "A01",
        "status" : "Complete",
        "instrumentId" : "54999",
        "startedAt" : "2016-12-19T16:12:47.014Z",
        "uniqueId" : "7cf74b62-c6b8-431d-b8ae-7e28cfd8343b",
        "collectionPathUri" : "/data/sequel/r54999_20161219_160902/1_A01",
        "runId" : "a836efbc-fd58-40f6-b586-43c743730fe0",
        "movieMinutes" : 120
    }, {
        "name" : "54999_SAT_2ndCell",
        "instrumentName" : "Sequel",
        "context" : "m54999_161219_184813",
        "well" : "B01",
        "status" : "Ready",
        "instrumentId" : "54999",
        "startedAt" : "2016-12-19T16:12:47.014Z",
        "uniqueId" : "08af5ab4-7cf4-4d13-9bcb-ae977d493f04",
        "collectionPathUri" : "/data/sequel/r54999_20161219_160902/2_B01",
        "runId" : "a836efbc-fd58-40f6-b586-43c743730fe0",
        "movieMinutes" : 120
    }
    ]


In the above example, the first Collection has “status”, “Complete”.

You can take its UUID, i.e. “uniqueId”: “7cf74b62-c6b8-431d-b8ae-7e28cfd8343b”, and get its QC metrics. For instructions, see `How to get QC reports for a particular Collection`__.

The second Collection has “status” : “Ready”.

You can take its UUID, i.e. “uniqueId”: “08af5ab4-7cf4-4d13-9bcb-ae977d493f04”, and monitor its status until it becomes “Complete”; use the following API call:

.. code-block:: bash

    GET /smrt-link/runs/a836efbc-fd58-40f6-b586-43c743730fe0/collections/08af5ab4-7cf4-4d13-9bcb-ae977d493f04

Once this Collection becomes complete, you can get its QC metrics as
well.


How to import a completed collection (dataset)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Once a run is complete and the data have been transfered off the instrument,
the resulting dataset(s) can be imported into SMRT Link.  This will create
an `import-dataset` job that runs asynchronously and generates several reports
used to assess run quality.

To import a dataset, use this API call:

.. code-block:: bash

  POST /smrt-link/job-manager/jobs/import-dataset

The request body in this case is very simple:

.. code-block:: json

  {
    "datasetType": "PacBio.DataSet.SubreadSet",
    "path": "/data/sequel/r54999_20161219_160902/1_A01/m54999_20161219_170101.subreadset.xml"
  }

The server should respond with **201: Created** and the model for the new job:

.. code-block:: javascript

  {
    "name": "import-dataset",
    "updatedAt": "2018-06-19T21:13:31.047Z",
    "workflow": "{}",
    "path": "/smrtlink/userdata/jobs_root/000/000001",
    "state": "CREATED",
    "tags": "",
    "uuid": "7cf74b62-c6b8-431d-b8ae-7e28cfd8343b",
    "projectId": 1,
    "jobTypeId": "import-dataset",
    "id": 1,
    "smrtlinkVersion": "6.0.0.SNAPSHOT38748",
    "comment": "Description for job Import PacBio DataSet",
    "createdAt": "2018-06-19T21:13:31.047Z",
    "isActive": true,
    "createdBy": null,
    "isMultiJob": false,
    "jsonSettings": "{\"path\":\"/data/sequel/r54999_20161219_160902/1_A01/m54999_20161219_170101.subreadset.xml\",\"datasetType\":\"PacBio.DataSet.SubreadSet\",\"submit\":true}",
    "jobUpdatedAt": "2018-06-19T21:13:31.047Z",
  }

Client code should now block until the job is complete, which should result
in the "state" field changing to "SUCCESSFUL" if all goes well.  For this
particular job type it should only take several minutes at most to complete.

Note that the same ``import-dataset`` job type is also used to import other
dataset types such as the ReferenceSet XML used to run the SAT pipeline.


How to capture Run level summary metrics
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Run-level summary metrics are captured in the QC reports. See the following sections:

-  `How to get QC reports for a particular SMRT Link Run`__.

-  `How to get QC reports for a particular Collection`__.


How to get recent Runs
~~~~~~~~~~~~~~~~~~~~~~

To get recent Runs, perform the following steps:

1. Get the list of all Runs: Use the GET request with the following endpoint:

.. code-block:: bash

    GET /smrt-link/runs

2. Filter the response based on the value of the "createdAt" field. For
example:

"createdAt": "2016-12-13T19:11:54.086Z"

    **Note:** You may also search Runs based on specific criteria, such
    as reserved state, creator, or summary substring.

Example, suppose you want to find all Runs created on or after 01.01.2017. First, get the list of all Runs:


.. code-block:: bash

    GET /smrt-link/runs

The response will be an array of Run objects, as in the following example (some fields are removed for display purposes):


.. code-block:: javascript

    [{
        “name” : “2016-11-08_3150473_2kLambda_A12”,
        “uniqueId” : “97286726-b243-45b3-82f7-8b5f58c56d53”,
        “createdAt” : “2016-11-08T17:50:57.955Z”,
        “summary” : “lambdaNEB”
    }, {
        “name” : “2017_01_24_A7_4kbSymAsym_DS_3150540”,
        “uniqueId” : “abd8f5ec-a177-4d41-8556-81c5ffb6b0aa”,
        “createdAt” : “2017-01-24T20:09:27.629Z”,
        “summary” : “pBR322_InsertOnly”
    }, {
        “name” : “SMS_GoatVer_VVC034_3150433_2kLambda_400pm_SNR10.5”,
        “uniqueId” : “b81de65a-8018-4843-9da7-ff2647a9d01e”,
        “createdAt” : “2016-10-17T23:36:35.000Z”,
        “summary” : “lambdaNEB”
    }]

Now, search the above response for all Run objects whose “createdAt” field starts with the “2017-01” substring. From the above example, you will get two Runs that fit your criteria (that is, created on or after 01.01.2017):

Run with “name” equal to “2017_01_24_A7_4kbSymAsym_DS_3150540”,

Run with “name” equal to “2017_01_21_A7_RC0_2.5-6kb_DS”.


How to get the SMRT Link reports for dataset by UUID
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To get reports for a dataset, given the dataset UUID, perform the following steps:

1. Determine the dataset type from the list of available dataset types. Use the GET request with the following endpoint:

.. code-block:: bash

    GET /smrt-link/dataset-types

2. Get the corresponding dataset type string. The dataset type can be found in the "shortName" field. Dataset types are explained in `Overview of Dataset
Service <#Overview_of_Dataset_Service>`__.

3. Get reports that correspond to the dataset. Given the dataset UUID and the dataset type, use them in the GET request with the following endpoint:

.. code-block:: bash

    GET /smrt-link/datasets/{datasetType}/{datasetUUID}/reports


Example

To get reports associated with a subreadset with UUID = 146338e0-7ec2-4d2d-b938-11bce71b7ed1, perform the following steps:

Use the GET request with the following endpoint:


.. code-block:: bash

    GET /smrt-link/dataset-types

You see that the shortName of SubreadSets is “subreads”. The desired endpoint is:

.. code-block:: bash

    /smrt-link/datasets/subreads/7cf74b62-c6b8-431d-b8ae-7e28cfd8343b/reports

Use the GET request with this endpoint to get reports that correspond to the SubreadSet with UUID = 7cf74b62-c6b8-431d-b8ae-7e28cfd8343b:


.. code-block:: bash

    GET /smrt-link/datasets/subreads/7cf74b62-c6b8-431d-b8ae-7e28cfd8343b/reports

Once you have the UUID for an individual report, it can be downloaded using
the datastore files service:
the ``uuid`` field

.. code-block:: bash

    GET /smrt-link/datastore-files/519817b6-4bfe-4402-a54e-c16b29eb06eb/download


How to get QC reports for a particular Collection
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

For completed Collections, the Collection UUID will be the same as
the UUID of the SubreadSet for that Collection. To retrieve the QC
reports of a completed Collection, given the Collection UUID,
perform the following steps:

1. Get the QC reports that correspond to this Collection: Use the GET request with the following endpoint:

.. code-block:: bash

    GET /smrt-link/datasets/subreads/{collectionUUID}/reports

See `How to get the SMRT Link reports for dataset by UUID`__ for more details.

**Note:** Obtaining dataset reports based on the Collection UUID as described above will only work if the Collection is **complete**. If the Collection is **not** complete, then the SubreadSet does not exist yet.


How to get QC reports for a particular SMRT Link Run
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To get QC reports for a particular Run, given the Run Name, perform the following steps:

1. Get the list of all Runs: Use the GET request with the following endpoint:

.. code-block:: bash

    GET /smrt-link/runs

In the response, perform a text search for the Run Name: Find the object whose “name” field is equal to the Run Name, and get the Run UUID, which can be found in the “uniqueId” field.

2. Get all Collections that belong to this Run: Use the Run UUID found in the previous step in the GET request with the following endpoint:

.. code-block::

    GET /smrt-link/runs/{runUUID}/collections

3. Take a Collection UUID of one of Collection objects received in the previous response. The Collection UUIDs can be found in the "uniqueId" fields.

For **complete** Collections, the Collection UUID will be the same as the UUID of the SubreadSet for that Collection.

Make sure that the Collection whose “uniqueId” field you take has the field “status” set to “Complete”. This is because obtaining dataset reports based on the Collection UUID as described below will **only** work if the Collection is **complete**. If the Collection is **not** complete, the SubreadSet does not exist yet.

You can now retrieve the QC reports that correspond to this Collection as
described above in `How to get the SMRT Link reports for dataset by UUID`__.

4. Repeat Step 3 to download QC reports for all complete Collections of that Run.


Example

You view the Run QC page in the SMRT Link UI, and open the page of a Run
with status “Complete”. Take the Run Name and look for the Run UUID in
the list of all Runs, as described above.

**Note:** The Run ID will also appear in the {runUUID} path parameter of the SMRT Link UI URL

.. code-block:: bash

    http://SMRTLinkServername.domain:9090/#/run-qc/{runUUID}

So the shorter way would be to take the Run UUID directly from the URL, such as

.. code-block:: bash

    http://SMRTLinkServername.domain:9090/#/run-qc/a836efbc-fd58-40f6-b586-43c743730fe0

With this Run UUID = a836efbc-fd58-40f6-b586-43c743730fe0, get all Collections that belong to this Run:

.. code-block:: bash

    GET /smrt-link/runs/a836efbc-fd58-40f6-b586-43c743730fe0/collections

Take a UUID of a completed Collection, such as “uniqueId”: "59230aeb-a8e3-4b46-b1b1-24c782c158c1". With this Collection UUID, retrieve QC reports of the corresponding SubreadSet:

.. code-block:: bash

    GET /smrt-link/datasets/subreads/7cf74b62-c6b8-431d-b8ae-7e28cfd8343b/reports

Take a UUID of some report, such as. “uuid”: “00c310ab-e989-4978-961e-c673b9a2b027”. With this report UUID, download the corresponding report file:


.. code-block:: bash

    GET /smrt-link/datastore-files/00c310ab-e989-4978-961e-c673b9a2b027/download

Repeat the last two API calls until you download all desired reports for all complete Collections.


How to setup a SMRT Link Analysis Job for a specific Pipeline
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To create an analysis job for a specific pipeline, you need to create a job of type “pbsmrtpipe” with the payload based on the template of the desired pipeline. Perform the following steps:

1. Get the list of all pipeline templates used for creating analysis jobs:

.. code-block:: bash

    GET /smrt-link/resolved-pipeline-templates

1. In the response, search for the name of the specific pipeline that
   you want to set up. Once the desired template is found, note the
   values of the pipeline “id” and “entryPoints” elements of that
   template.

2. Get the datasets list that corresponds to the type specified in the
   first element of “entryPoints” array. For example, for the type
   “fileTypeId” : “PacBio.DataSet.SubreadSet”, get the list of
   “subreads” datasets:

.. code-block:: bash

    GET /smrt-link/datasets/subreads

4. Repeat step 3. for the dataset types specified in the rest of elements of “entryPoints” array.

5. From the lists of datasets brought on steps 3. and 4, select IDs of the datasets that you want to use as entry points for the pipeline you are about to set up.

6. Build the request body for creating a job of type "pbsmrtpipe".  The
basic structure looks like this:

.. code-block:: javascript

    {
        "entryPoints": [
            {
                "datasetId": 2,
                "entryId": "eid_subread",
                "fileTypeId": "PacBio.DataSet.SubreadSet"
            },
            {
                "datasetId": 1,
                "entryId": "eid_ref_dataset",
                "fileTypeId": "PacBio.DataSet.ReferenceSet"
            }
        ],
        "name": "Lambda SAT job",
        "pipelineId": "pbsmrtpipe.pipelines.sa3_sat",
        "taskOptions": [],
        "workflowOptions": []
    }

Use the pipeline “id” found on step 2 as the value for “pipelineId” element.

Use dataset types of “entryPoints” array found on step 2 and corresponding dataset IDs found on step 5 as the values for elements of “entryPoints” array.

Note that “taskOptions” array is optional and may be completely empty in the request body.

7. Create a job of type “pbsmrtpipe”.

Use the request body built in the previous step in the POST request with the following endpoint:


.. code-block:: bash

    POST /smrt-link/job-manager/jobs/pbsmrtpipe

8. You may monitor the state of the job created on step 7 with the use of the following request:


.. code-block:: bash

    GET /smrt-link/job-manager/jobs/pbsmrtpipe/{jobID}/events

Where jobID is equal to the value received in “id” element of the response on step 7.


Example

Suppose you want to setup an analysis job for Resequencing pipeline.

First, get the list of all pipeline templates used for creating analysis jobs:


.. code-block::

    GET /smrt-link/resolved-pipeline-templates


The response will be an array of pipeline template objects. In this response, do the search for the entry with “name” : “Resequencing”. The entry may look as in the following example:

.. code-block:: javascript

    {
        “name” : “Resequencing”,
        “id” : “pbsmrtpipe.pipelines.sa3_ds_resequencing_fat”,
        “description” : “Full Resequencing Pipeline - Blasr mapping and Genomic Consensus.”,
        “version” : “0.1.0”,
        “entryPoints” : [{
          “entryId” : “eid_subread”, “fileTypeId” : “PacBio.DataSet.SubreadSet”, “name” : “Entry Name: PacBio.DataSet.SubreadSet”}, {
          “entryId” : “eid_ref_dataset”, “fileTypeId” : “PacBio.DataSet.ReferenceSet”, “name” : “Entry Name: PacBio.DataSet.ReferenceSet”}
        ],
        “tags” : [ “consensus”, “reports”],
        “taskOptions” : [{
            "optionTypeId": "choice_string",
            "name": "Algorithm",
            "choices": ["quiver", "arrow", "plurality", "poa", "best"],
            "description": "Variant calling algorithm",
            "id": "genomic_consensus.task_options.algorithm",
            "default": "best"
        }]
    }

In the above entry, take the value of the pipeline “id” : “pbsmrtpipe.pipelines.sa3_ds_resequencing_fat”.

Also, take the dataset types of “entryPoints” elements: “fileTypeId” : “PacBio.DataSet.SubreadSet” and “fileTypeId” : “PacBio.DataSet.ReferenceSet”.

Now, get the lists of the datasets that correspond to the types
specified in the elements of the “entryPoints” array.

In particular, for the type “fileTypeId” : “PacBio.DataSet.SubreadSet”, get the list of “subreads” datasets:

.. code-block:: bash

    GET /smrt-link/datasets/subreads

And for the type “fileTypeId” : “PacBio.DataSet.ReferenceSet”, get the list of “references” datasets:


.. code-block:: bash

    GET /smrt-link/datasets/references

From the above lists of datasets, select IDs of the datasets that you
want to use as entry points for the Resequencing pipeline you are about
to setup.

For example, take the dataset with “id”: 18 from the “subreads” list and
the dataset with “id”: 2 from the “references” list.

Build the request body for creating ‘pbsmrtpipe’ job for Resequencing
pipeline.

Use the pipeline “id” obtained above as the value for “pipelineId”
element.

Use these two dataset IDs obtained above as values of the “datasetId”
fields in the “entryPoints” array. For example:


.. code-block:: javascript

    {
        “pipelineId” : “pbsmrtpipe.pipelines.sa3_ds_resequencing_fat”,
        “entryPoints” : [
            {
                “entryId” : “eid_subread”,
                “fileTypeId” : “PacBio.DataSet.SubreadSet”,
                “datasetId” : 18
            },
            {
                “entryId” : “eid_ref_dataset”,
                “fileTypeId” : “PacBio.DataSet.ReferenceSet”,
                “datasetId” : 2
            }
        ],
        “taskOptions” : [],
        "workflowOptions": [],
        "name": "My Resequencing Job"
    }

Now create a job of type “pbsmrtpipe”.

Use the request body built above in the following API call:

.. code-block:: bash

    POST /smrt-link/job-manager/jobs/pbsmrtpipe


Verify that the job was created successfully. The return HTTP status
should be **201 Created**.


Querying Job History
~~~~~~~~~~~~~~~~~~~~

The job service endpoints provide a number of search criteria (plus paging
support) that can be used to limit the return results.  A full list of
available search criteria is provided in the the JSON Swagger API definition
for the jobs endpoint.  The following search retrieves all failed Site
Acceptance Test (SAT) pipeline jobs:

.. code-block:: bash

    GET /smrt-link/job-manager/jobs/pbsmrtpipe?state=FAILED&subJobTypeId=pbsmrtpipe.pipelines.sa3_sat

For most datatypes additional operators besides equality are allowed, for example:

.. code-block:: bash
    GET /smrt-link/job-manager/jobs/pbsmrtpipe?createdAt=lt%3A2018-03-01T00:00:00.000Z&createdBy=myusername


This retrieves all pbsmrtpipe jobs run before 2018-03-01 by a user with the
login ID "myusername".  (Note that certain searches, especially partial text
searches using `like:`, may be significantly slower to execute and can overload
the server if called too frequently.)


How to delete a SMRT Link Job
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


To delete a job, you need to create another job of type “delete-job”, and pass the UUID of the job to delete in the payload (a.k.a. request body).

Perform the following steps:

1. Build the payload for the POST request as a JSON with the following
   fields:

-  **jobId**: The UUID of the job to be deleted.

-  **removeFiles**: A boolean flag specifying whether to remove files
   associated with the job being deleted.

-  **dryRun**: A boolean flag allowing to check whether it is safe to
   delete the job prior to actually deleting it.

    **Note:** If you want to make sure that it is safe to delete the job
    (there is no other piece of data dependent on the job being
    deleted), then first set the the “dryRun” field to ‘true’ and
    perform the API call described in Step 2 below. If the call
    succeeds, meaning that the job can be safely deleted, set the
    “dryRun” field to ‘false’ and repeat the same API call again, as
    described in Step 3 below.

1. Check whether the job can be deleted, without actually changing
       anything in the database or on disk.

  Create a job of type “delete-job” with the payload which has ``dryRun = true``; use the POST request with the following endpoint:

.. code-block:: bash

    POST /smrt-link/job-manager/jobs/delete-job

1. If the previous API call succeeded, that is, the job may be safely
   deleted, then proceed with actually deleting the job.

    Create a job of type “delete-job” with the payload which has dryRun
    = false; use the POST request with the following endpoint:

.. code-block:: bash

    POST /smrt-link/job-manager/jobs/delete-job


Suppose you want to delete the job with UUID = 13957a79-1bbb-44ea-83f3-6c0595bf0d42. Define the payload as in the following example, and set the “dryRun” field in it to ‘true’:


.. code-block:: javascript

    {
        “jobId” : “13957a79-1bbb-44ea-83f3-6c0595bf0d42”,
        “removeFiles” :true,
        “dryRun” : true
    }

Create a job of type “delete-job”, using the above payload in the
following POST request:

.. code-block:: bash

    POST /smrt-link/job-manager/jobs/delete-job

Verify that the response status is **201: Created**.

Also notice that the response body contains JSON corresponding to the job to be deleted, as in the following example:


.. code-block:: javascript

    {
        “name” : “Job merge-datasets”,
        “uuid” : “13957a79-1bbb-44ea-83f3-6c0595bf0d42”,
        “jobTypeId” : “merge-datasets”,
        “id” : 53,
        “createdAt” : “2016-01-29T00:09:58.462Z”,
        ...
        “comment” : “Merging Datasets MergeDataSetOptions(PacBio.DataSet.SubreadSet, Auto-merged subreads @1454026198403)”
    }

Define the payload as in the following example, and this time set the “dryRun” field to ‘false’, to actually delete the job:


.. code-block:: javascript

    {
        “jobId” : “13957a79-1bbb-44ea-83f3-6c0595bf0d42”,
        “removeFiles” : true,
        “dryRun” : false
    }

Create a job of type “delete-job”, using the above payload in the following POST request:


.. code-block:: bash

    POST /smrt-link/job-manager/jobs/delete-job

Verify that the response status is **201: Created**. Notice that this time the response body contains JSON corresponding to the job of type “delete-job”, as in the following example:

.. code-block:: javascript

    {
        “name” : “Job delete-job”,
        “uuid” : “1f60c976-e426-43b5-8ced-f8139de6ceff”,
        “jobTypeId” : “delete-job”,
        “id” : 7666,
        “createdAt” : “2017-03-09T11:51:38.828-08:00”,
        ...
        “comment” : “Deleting job 13957a79-1bbb-44ea-83f3-6c0595bf0d42”
    }

Clients should then block until the job is complete.


    For Research Use Only. Not for use in diagnostic procedures. ©
    Copyright 2015 - 2017, Pacific Biosciences of California, Inc. All
    rights reserved. Information in this document is subject to change
    without notice. Pacific Biosciences assumes no responsibility for
    any errors or omissions in this document. Certain notices, terms,
    conditions and/or use restrictions may pertain to your use of
    Pacific Biosciences products and/or third party products. Please
    refer to the applicable Pacific Biosciences Terms and Conditions of
    Sale and to the applicable license terms at
    `http://www.pacb.com/legal-and-trademarks/product-license-and-use-restrictions/. <http://www.pacb.com/legal-and-trademarks/product-license-and-use-restrictions/>`__

    Pacific Biosciences, the Pacific Biosciences logo, PacBio, SMRT,
    SMRTbell, Iso-Seq and Sequel are trademarks of Pacific Biosciences.
    BluePippin and SageELF are trademarks of Sage Science, Inc. NGS-go
    and NGSengine are trademarks of GenDx. FEMTO Pulse and Fragment
    Analyzer are trademarks of Advanced Analytical Technologies. All
    other trademarks are the sole property of their respective owners.

P/N 100-855-900-04

.. |image0| image:: media/image1.png
   :width: 2.30303in
   :height: 0.77113in

Fetch Job Datastore
===================

Fetch datastore with datastore files, emitted from a job identified by job type and job id.

.. note:: Job type identifiers and descriptions are available in the response to :doc:`list_all_job_types` request.

Request
-------
+------------+---------------------------------------------------------------------------------------------------+
| **Method** | **URI**                                                                                           |
+============+===================================================================================================+
| GET        | http://<host>:<port>/secondary-analysis/job-manager/jobs/{jobTypeId}/{jobId}/datastore            |
+------------+---------------------------------------------------------------------------------------------------+

|

+----------------------+-----------------+--------------------------------+----------------+--------------------+---------------------------+
| **Path Parameters**  | **Data Type**   | **Description**                | **Required**   | **Multi-valued**   | **Possible Values**       |
+======================+=================+================================+================+====================+===========================+
| jobTypeId            | string          | Job type / category;           | Yes            | No                 | | import-dataset          |
|                      |                 | list of all possible job types |                |                    | | import-datastore        |
|                      |                 | may be obtained through        |                |                    | | merge-datasets          |
|                      |                 | :doc:`list_all_job_types`      |                |                    | | convert-rs-movie        |
|                      |                 | request.                       |                |                    | | convert-fasta-reference |
|                      |                 |                                |                |                    | | pbsmrtpipe              |
+----------------------+-----------------+--------------------------------+----------------+--------------------+---------------------------+
| jobId                | integer         | Unique identifier of a job     | Yes            | No                 | | 5                       |
|                      |                 | within its job type            |                |                    | | 28                      |
+----------------------+-----------------+--------------------------------+----------------+--------------------+---------------------------+

|

+---------------------+-----------------------------------------------------+--------------+-------------------+
| **Request Headers** | **Description**                                     | **Required** | **Sample Value**  |
+=====================+=====================================================+==============+===================+
| Accept              | Content-Types that are acceptable for the response. | Yes          | application/json  |
+---------------------+-----------------------------------------------------+--------------+-------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Request Body Representation / Schema**               |
+==================+========================================================+
| application/json | Request schema is N/A since this is GET request        |
+------------------+--------------------------------------------------------+

Sample Request
~~~~~~~~~~~~~~
| Fetch datastore with datastore files emitted from the job of type 'import-dataset' with ID=17:
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/job-manager/jobs/import-dataset/17/datastore
|
| Fetch datastore with datastore files emitted from the job of type 'merge-datasets' with ID=252:
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/job-manager/jobs/merge-datasets/252/datastore
|
| Fetch datastore with datastore files emitted from the job of type 'convert-fasta-reference' with ID=4:
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/job-manager/jobs/convert-fasta-reference/4/datastore
|
| Fetch datastore with datastore files emitted from the job of type 'pbsmrtpipe' with ID=38:
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/job-manager/jobs/pbsmrtpipe/38/datastore

Response
--------
+-----------------------+-----------------------------------------------+------------------------------------------------------------------------------------+
| **HTTP Status Code**  | **Error Message**                             | **Description**                                                                    |
+=======================+===============================================+====================================================================================+
| 200 OK                | None                                          | Successful completion of the request.                                              |
|                       |                                               | Note: if there is no job corresponding to jobId value passed in the request, or    |
|                       |                                               | the job exists but does not have datastore files, then the response still will be  |
|                       |                                               | 200 OK, with empty array of datastore files in the response body.                  |
+-----------------------+-----------------------------------------------+------------------------------------------------------------------------------------+
| 404 Not Found         | The requested resource could not be found.    | Occurs when invalid value of jobTypeId (non-existing job type) is passed           |
|                       |                                               | in the request. Note: in order to get the list of valid jobTypeId values, use      |
|                       |                                               | :doc:`list_all_job_types` request.                                                 |
+-----------------------+-----------------------------------------------+------------------------------------------------------------------------------------+
| 406 Not Acceptable    | Resource representation is only available     | Occurs when invalid value of Accept header was passed in the request,              |
|                       | with these Content-Types: application/json;   | for example: Accept: application/xml                                               |
|                       | charset=UTF-8                                 |                                                                                    |
+-----------------------+-----------------------------------------------+------------------------------------------------------------------------------------+

|

+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| **Response Headers**          | **Description**                                                               | **Required**   | **Sample Value**                  |
+===============================+===============================================================================+================+===================================+
| Access-Control-Allow-Origin   | Specifying which web sites can participate in cross-origin resource sharing.  | Yes            | \*                                |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Date                          | The date and time that the message was sent.                                  | Yes            | Fri, 05 Feb 2016 00:44:28 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 957                               |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**              |
+==================+========================================================+
| application/json | See `Fetch job datastore - response schema`_           |
+------------------+--------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `Fetch job datastore - response sample`_

Additional info
---------------
+-----------------------------------+----------------------------------------------+
| **Authentication**                | **Technical Support**                        |
+===================================+==============================================+
| No authentication required.       | E-mail: support@pacb.com                     |
+-----------------------------------+----------------------------------------------+

Change Log
~~~~~~~~~~
+----------------------+------------------------------------+--------------------------+
| **Release**          | **Description of changes**         | **Backward-compatible**  |
+======================+====================================+==========================+
| SMRT Analysis 3.0    | New service endpoint.              | N/A                      |
+----------------------+------------------------------------+--------------------------+


.. _`Fetch job datastore - response schema`:

Fetch job datastore - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: job_datastore_schema.json
    :language: javascript

.. _`Fetch job datastore - response sample`:

Fetch job datastore - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Sample 1 - fetch datastore with datastore files emitted from the job of type 'import-dataset' with ID=17:
|

.. literalinclude:: get_jobs_import-dataset_17_datastore.json
    :language: javascript

|
| Sample 2 - fetch datastore with datastore files emitted from the job of type 'merge-datasets' with ID=252:
|

.. literalinclude:: get_jobs_merge-datasets_252_datastore.json
    :language: javascript

|
| Sample 3 - fetch datastore with datastore files emitted from the job of type 'convert-fasta-reference' with ID=4:
|

.. literalinclude:: get_jobs_convert-fasta-reference_4_datastore.json
    :language: javascript

|
| Sample 4 - fetch datastore with datastore files emitted from the job of type 'pbsmrtpipe' with ID=38:
|

.. literalinclude:: get_jobs_pbsmrtpipe_38_datastore.json
    :language: javascript



Fetch Job by Type and Id
========================

Fetch the record of a job object by its ID within its job type.

.. note:: Job type identifiers and descriptions are available in the response to :doc:`list_all_job_types` request.

Request
-------
+------------+-----------------------------------------------------------------------------------------+
| **Method** | **URI**                                                                                 |
+============+=========================================================================================+
| GET        | http://<host>:<port>/secondary-analysis/job-manager/jobs/{jobTypeId}/{jobId}            |
+------------+-----------------------------------------------------------------------------------------+

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
| jobId                | integer         | Unique identifier of a job     | Yes            | No                 | | 3                       |
|                      |                 | within its job type            |                |                    | | 40                      |
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
| Fetch the record of the job object of type 'merge-datasets' with ID=252:
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/job-manager/jobs/merge-datasets/252
|
| Fetch the record of the job object of type 'pbsmrtpipe' with ID=38:
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/job-manager/jobs/pbsmrtpipe/38

Response
--------
+-----------------------+-----------------------------------------------+------------------------------------------------------------------------------------+
| **HTTP Status Code**  | **Error Message**                             | **Description**                                                                    |
+=======================+===============================================+====================================================================================+
| 200 OK                | None                                          | Successful completion of the request.                                              |
+-----------------------+-----------------------------------------------+------------------------------------------------------------------------------------+
| 404 Not Found         | The requested resource could not be found.    | Occurs when invalid value of jobTypeId (non-existing job type) is passed           |
|                       |                                               | in the request. Note: in order to get the list of valid jobTypeId values, use      |
|                       |                                               | :doc:`list_all_job_types` request.                                                 |
+-----------------------+-----------------------------------------------+------------------------------------------------------------------------------------+
| 404 Not Found         | Unable to find JobId 3888.                    | Occurs when invalid value of ID (non-existing job identifier within its job type)  |
|                       |                                               | is passed in the request. Note: in order to get the list of valid ID values, use   |
|                       |                                               | :doc:`list_all_jobs_by_type` request.                                              |
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
| Date                          | The date and time that the message was sent.                                  | Yes            | Tue, 09 Feb 2016 00:45:10 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 6231                              |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**              |
+==================+========================================================+
| application/json | See `Fetch job by type and id - response schema`_      |
+------------------+--------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `Fetch job by type and id - response sample`_

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


.. _`Fetch job by type and id - response schema`:

Fetch job by type and id - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: job_object_schema.json
    :language: javascript

.. _`Fetch job by type and id - response sample`:

Fetch job by type and id - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Sample 1 - fetch the record of the job object of type 'merge-datasets' with ID=252:
|

.. literalinclude:: get_jobs_merge-datasets_252.json
    :language: javascript

|
| Sample 2 - fetch the record of the job object of type 'pbsmrtpipe' with ID=38:
|

.. literalinclude:: get_jobs_pbsmrtpipe_38.json
    :language: javascript


Fetch Job Reports
=================

Fetch job report files, emitted from a job identified by job type and job id.

.. note:: Job type identifiers and descriptions are available in the response to :doc:`list_all_job_types` request.

Request
-------
+------------+-------------------------------------------------------------------------------------------------------+
| **Method** | **URI**                                                                                               |
+============+=======================================================================================================+
| GET        | http://<host>:<port>/secondary-analysis/job-manager/jobs/{jobTypeId}/{jobId}/reports                  |
+------------+-------------------------------------------------------------------------------------------------------+

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
| jobId                | integer         | Unique identifier of a job     | Yes            | No                 | | 11                      |
|                      |                 | within its job type            |                |                    | | 252                     |
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
| Fetch job report files emitted from the job of type 'import-dataset' with ID=5:
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/job-manager/jobs/import-dataset/5/reports
|
| Fetch job report files emitted from the job of type 'pbsmrtpipe' with ID=38:
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/job-manager/jobs/pbsmrtpipe/38/reports

Response
--------
+-----------------------+-----------------------------------------------+------------------------------------------------------------------------------------+
| **HTTP Status Code**  | **Error Message**                             | **Description**                                                                    |
+=======================+===============================================+====================================================================================+
| 200 OK                | None                                          | Successful completion of the request.                                              |
|                       |                                               | Note: if there is no job corresponding to jobId value passed in the request, or    |
|                       |                                               | the job exists but does not have report files, then the response still will be     |
|                       |                                               | 200 OK, with empty array of report files in the response body.                     |
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
| Date                          | The date and time that the message was sent.                                  | Yes            | Tue, 09 Feb 2016 00:57:13 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 649                               |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**              |
+==================+========================================================+
| application/json | See `Fetch job reports - response schema`_             |
+------------------+--------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `Fetch job reports - response sample`_

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


.. _`Fetch job reports - response schema`:

Fetch job reports - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: job_reports_schema.json
    :language: javascript

.. _`Fetch job reports - response sample`:

Fetch job reports - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Sample 1 - fetch job report files emitted from the job of type 'import-dataset' with ID=5:
|

.. literalinclude:: get_jobs_import-dataset_5_reports.json
    :language: javascript

|
| Sample 2 - fetch job report files emitted from the job of type 'pbsmrtpipe' with ID=38:
|

.. literalinclude:: get_jobs_pbsmrtpipe_38_reports.json
    :language: javascript



List All Jobs by Type
=====================

Get the list of all job objects of a specific type.

.. note:: Job type identifiers and descriptions are available in the response to :doc:`list_all_job_types` request.

Request
-------
+------------+---------------------------------------------------------------------------------+
| **Method** | **URI**                                                                         |
+============+=================================================================================+
| GET        | http://<host>:<port>/secondary-analysis/job-manager/jobs/{jobTypeId}            |
+------------+---------------------------------------------------------------------------------+

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
| Get the list of all jobs of type 'import-dataset':
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/job-manager/jobs/import-dataset
|
| Get the list of all jobs of type 'convert-fasta-reference':
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/job-manager/jobs/convert-fasta-reference

Response
--------
+-----------------------+-----------------------------------------------+------------------------------------------------------------------------------------+
| **HTTP Status Code**  | **Error Message**                             | **Description**                                                                    |
+=======================+===============================================+====================================================================================+
| 200 OK                | None                                          | Successful completion of the request. Note: if there are no jobs corresponding     |
|                       |                                               | to job type specified in {jobTypeId}, then the response still will be 200 OK,      |
|                       |                                               | with empty jobs array in the response body.                                        |
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
| Date                          | The date and time that the message was sent.                                  | Yes            | Thu, 04 Feb 2016 23:06:16 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 1211150                           |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**              |
+==================+========================================================+
| application/json | See `List all jobs by type - response schema`_         |
+------------------+--------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `List all jobs by type - response sample`_

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


.. _`List all jobs by type - response schema`:

List all jobs by type - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: jobs_list_schema.json
    :language: javascript

.. _`List all jobs by type - response sample`:

List all jobs by type - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Sample 1 - get the list of all jobs of type 'import-dataset':
|

.. literalinclude:: list_jobs_import-dataset.json
    :language: javascript

|
| Sample 2 - get the list of all jobs of type 'convert-fasta-reference':
|

.. literalinclude:: list_jobs_convert-fasta-reference.json
    :language: javascript


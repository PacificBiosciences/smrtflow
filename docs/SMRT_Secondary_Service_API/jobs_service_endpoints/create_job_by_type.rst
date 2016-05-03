Create Job by Type
==================

Create a new job of a specific type.

.. note:: Job type identifiers and descriptions are available in the response to :doc:`list_all_job_types` request.

Request
-------
+------------+---------------------------------------------------------------------------------+
| **Method** | **URI**                                                                         |
+============+=================================================================================+
| POST       | http://<host>:<port>/secondary-analysis/job-manager/jobs/{jobTypeId}            |
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
| Content-Type        | The MIME type of the content in the request.        | Yes          | application/json  |
+---------------------+-----------------------------------------------------+--------------+-------------------+
| Accept              | Content-Types that are acceptable for the response. | Yes          | application/json  |
+---------------------+-----------------------------------------------------+--------------+-------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Request Body Representation / Schema**               |
+==================+========================================================+
| application/json | See `Create job by type - request schema`_             |
+------------------+--------------------------------------------------------+

Sample Request
~~~~~~~~~~~~~~

See `Create job by type - request sample`_

Response
--------
+----------------------------+---------------------------------------------------------+------------------------------------------------------------------------------+
| **HTTP Status Code**       | **Error Message**                                       | **Description**                                                              |
+============================+=========================================================+==============================================================================+
| 201 Created                | None                                                    | Successful completion of the request.                                        |
|                            |                                                         | The newly created job object will be returned in the response body.          |
+----------------------------+---------------------------------------------------------+------------------------------------------------------------------------------+
| 400 Bad Request            | The request content was malformed:                      | Occurs when "optionTypeId" field was omitted in "taskOptions" array's        |
|                            | Expected Task Option.                                   | elements, in the request body of create 'pbsmrtpipe' job request.            |
+----------------------------+---------------------------------------------------------+------------------------------------------------------------------------------+
| 404 Not Found              | The requested resource could not be found.              | Occurs when invalid value of jobTypeId (non-existing job type) was passed    |
|                            |                                                         | in the request. Note: in order to get the list of valid jobTypeId values,    |
|                            |                                                         | use :doc:`list_all_job_types` request.                                       |
+----------------------------+---------------------------------------------------------+------------------------------------------------------------------------------+
| 404 Not Found              | Unable to find Hdf subread dataset '129'                | Occurs when non-existing dataset identifier was  passed in the request body  |
|                            |                                                         | of create 'merge-datasets' job request.                                      |
+----------------------------+---------------------------------------------------------+------------------------------------------------------------------------------+
| 406 Not Acceptable         | Resource representation is only available               | Occurs when invalid value of Accept header was passed in the request,        |
|                            | with these Content-Types: application/json;             | for example: Accept: application/xml                                         |
|                            | charset=UTF-8                                           |                                                                              |
+----------------------------+---------------------------------------------------------+------------------------------------------------------------------------------+
| 415 Unsupported Media Type | There was a problem with the request's                  | Occurs when invalid value of Content-Type header was passed in the request,  |
|                            | Content-Type: Expected 'application/json'               | for example: Content-Type: application/x-www-form-urlencoded                 |
+----------------------------+---------------------------------------------------------+------------------------------------------------------------------------------+
| 422 Unprocessable Entity   | Failed to validate: com.pacbio.secondary.analysis.jobs. | Occurs when invalid path to input data file was passed in the request,       |
|                            | InvalidJobOptionError: Unable to find /tmp/file.fasta   | for example: "path": "/tmp/file.fasta"                                       |
+----------------------------+---------------------------------------------------------+------------------------------------------------------------------------------+

|

+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| **Response Headers**          | **Description**                                                               | **Required**   | **Sample Value**                  |
+===============================+===============================================================================+================+===================================+
| Access-Control-Allow-Origin   | Specifying which web sites can participate in cross-origin resource sharing.  | Yes            | \*                                |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Date                          | The date and time that the message was sent.                                  | Yes            | Tue, 23 Feb 2016 18:38:46 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 679                               |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**              |
+==================+========================================================+
| application/json | See `Create job by type - response schema`_            |
+------------------+--------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `Create job by type - response sample`_

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


.. _`Create job by type - request schema`:

Create job by type - request schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Schema 1 - request body schema to create a job of type 'import-dataset':
|

.. literalinclude:: post/create_import-dataset_job_request_schema.json
    :language: javascript

|
| Schema 2 - request body schema to create a job of type 'import-datastore':
|

.. literalinclude:: post/create_import-datastore_job_request_schema.json
    :language: javascript

|
| Schema 3 - request body schema to create a job of type 'merge-datasets':
|

.. literalinclude:: post/create_merge-datasets_job_request_schema.json
    :language: javascript

|
| Schema 4 - request body schema to create a job of type 'convert-rs-movie':
|

.. literalinclude:: post/create_convert-rs-movie_job_request_schema.json
    :language: javascript

|
| Schema 5 - request body schema to create a job of type 'convert-fasta-reference':
|

.. literalinclude:: post/create_convert-fasta-reference_job_request_schema.json
    :language: javascript

|
| Schema 6 - request body schema to create a job of type 'pbsmrtpipe':
|

.. literalinclude:: post/create_pbsmrtpipe_job_request_schema.json
    :language: javascript


.. _`Create job by type - request sample`:

Create job by type - request sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Sample 1 - request to create a job of type 'import-dataset':
|
| POST http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/job-manager/jobs/import-dataset/
|

.. literalinclude:: post/create_import-dataset_job_request_sample.json
    :language: javascript

|
| Sample 2 - request to create a job of type 'import-datastore':
|
| POST http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/job-manager/jobs/import-datastore/
|

.. literalinclude:: post/create_import-datastore_job_request_sample.json
    :language: javascript

|
| Sample 3 - request to create a job of type 'merge-datasets':
|
| POST http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/job-manager/jobs/merge-datasets/
|

.. literalinclude:: post/create_merge-datasets_job_request_sample.json
    :language: javascript

|
| Sample 4 - request to create a job of type 'convert-rs-movie':
|
| POST http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/job-manager/jobs/convert-rs-movie/
|

.. literalinclude:: post/create_convert-rs-movie_job_request_sample.json
    :language: javascript

|
| Sample 5 - request to create a job of type 'convert-fasta-reference':
|
| POST http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/job-manager/jobs/convert-fasta-reference/
|

.. literalinclude:: post/create_convert-fasta-reference_job_request_sample.json
    :language: javascript

|
| Sample 6 - request to create a job of type 'pbsmrtpipe':
|
| POST http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/job-manager/jobs/pbsmrtpipe/
|

.. literalinclude:: post/create_pbsmrtpipe_job_request_sample.json
    :language: javascript


.. _`Create job by type - response schema`:

Create job by type - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Schema of response body returned upon creation of a job of any type:
|

.. literalinclude:: post/create_any_job_response_schema.json
    :language: javascript


.. _`Create job by type - response sample`:

Create job by type - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Sample 1 - response returned upon creation of a job of type 'import-dataset':
|

.. literalinclude:: post/create_import-dataset_job_response_sample.json
    :language: javascript

|
| Sample 2 - response returned upon creation of a job of type 'import-datastore':
|

.. literalinclude:: post/create_import-datastore_job_response_sample.json
    :language: javascript

|
| Sample 3 - response returned upon creation of a job of type 'merge-datasets':
|

.. literalinclude:: post/create_merge-datasets_job_response_sample.json
    :language: javascript

|
| Sample 4 - response returned upon creation of a job of type 'convert-rs-movie':
|

.. literalinclude:: post/create_convert-rs-movie_job_response_sample.json
    :language: javascript

|
| Sample 5 - response returned upon creation of a job of type 'convert-fasta-reference':
|

.. literalinclude:: post/create_convert-fasta-reference_job_response_sample.json
    :language: javascript

|
| Sample 6 - response returned upon creation of a job of type 'pbsmrtpipe':
|

.. literalinclude:: post/create_pbsmrtpipe_job_response_sample.json
    :language: javascript


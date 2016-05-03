List All Datasets by Type
=========================

Get the list of all dataset objects of a specific type.

.. note:: Dataset type is specified by its short name; the short names of dataset types
   are available in the response to :doc:`list_all_dataset_types` request.

Request
-------
+------------+-----------------------------------------------------------------------+
| **Method** | **URI**                                                               |
+============+=======================================================================+
| GET        | http://<host>:<port>/secondary-analysis/datasets/{shortName}          |
+------------+-----------------------------------------------------------------------+

|

+----------------------+-----------------+--------------------------------+----------------+--------------------+---------------------------+
| **Path Parameters**  | **Data Type**   | **Description**                | **Required**   | **Multi-valued**   | **Possible Values**       |
+======================+=================+================================+================+====================+===========================+
| shortName            | string          | Short name of a dataset type;  | Yes            | No                 | | alignments              |
|                      |                 | list of all possible dataset   |                |                    | | barcodes                |
|                      |                 | types with their short names   |                |                    | | contigs                 |
|                      |                 | may be obtained through        |                |                    | | ccsalignments           |
|                      |                 | :doc:`list_all_dataset_types`  |                |                    | | ccsreads                |
|                      |                 | request; use values from       |                |                    | | hdfsubreads             |
|                      |                 | "shortName" fields of          |                |                    | | references              |
|                      |                 | Dataset Type objects.          |                |                    | | subreads                |
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
| Get the list of all datasets of type 'alignments':
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/datasets/alignments
|
| Get the list of all datasets of type 'hdfsubreads':
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/datasets/hdfsubreads

Response
--------
+-----------------------+-----------------------------------------------+----------------------------------------------------------------------------------------+
| **HTTP Status Code**  | **Error Message**                             | **Description**                                                                        |
+=======================+===============================================+========================================================================================+
| 200 OK                | None                                          | Successful completion of the request. Note: if there are no datasets corresponding     |
|                       |                                               | to dataset type specified in {shortName}, then the response still will be 200 OK,      |
|                       |                                               | with empty datasets array in the response body.                                        |
+-----------------------+-----------------------------------------------+----------------------------------------------------------------------------------------+
| 404 Not Found         | The requested resource could not be found.    | Occurs when invalid value of shortName (non-existing dataset type) is passed           |
|                       |                                               | in the request. Note: in order to get the list of valid shortName values, use          |
|                       |                                               | :doc:`list_all_dataset_types` request.                                                 |
+-----------------------+-----------------------------------------------+----------------------------------------------------------------------------------------+
| 406 Not Acceptable    | Resource representation is only available     | Occurs when invalid value of Accept header was passed in the request,                  |
|                       | with these Content-Types: application/json;   | for example: Accept: application/xml                                                   |
|                       | charset=UTF-8                                 |                                                                                        |
+-----------------------+-----------------------------------------------+----------------------------------------------------------------------------------------+

|

+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| **Response Headers**          | **Description**                                                               | **Required**   | **Sample Value**                  |
+===============================+===============================================================================+================+===================================+
| Access-Control-Allow-Origin   | Specifying which web sites can participate in cross-origin resource sharing.  | Yes            | \*                                |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Date                          | The date and time that the message was sent.                                  | Yes            | Tue, 26 Jan 2016 19:26:28 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 784856                            |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**              |
+==================+========================================================+
| application/json | See `List all datasets by type - response schema`_     |
+------------------+--------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `List all datasets by type - response sample`_

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


.. _`List all datasets by type - response schema`:

List all datasets by type - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Generic definition: Schema for the list of all dataset objects of 'shortName' type:
|

.. literalinclude:: datasets_generic_list_schema.json
    :language: javascript

.. note:: Schema definition of the datasets array's element ("pacbio.secondary.schemas.datasets.<shortName>")
   may be obtained through :doc:`get_dataset_schema_by_type` request.

| Example 1: Schema for the list of all dataset objects of 'alignments' type:
|

.. literalinclude:: datasets_alignments_list_schema.json
    :language: javascript

.. note:: Schema definition of the datasets array's element ("pacbio.secondary.schemas.datasets.alignments")
   may be obtained through :doc:`get_dataset_schema_by_type` request.

| Example 2: Schema for the list of all dataset objects of 'hdfsubreads' type:
|

.. literalinclude:: datasets_hdfsubreads_list_schema.json
    :language: javascript

.. note:: Schema definition of the datasets array's element ("pacbio.secondary.schemas.datasets.hdfsubreads")
   may be obtained through :doc:`get_dataset_schema_by_type` request.


.. _`List all datasets by type - response sample`:

List all datasets by type - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Sample 1 - get the list of all datasets of type 'alignments':
|

.. literalinclude:: get_datasets_alignments.json
    :language: javascript

|
| Sample 2 - get the list of all datasets of type 'hdfsubreads':
|

.. literalinclude:: get_datasets_hdfsubreads.json
    :language: javascript


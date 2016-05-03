Retrieve Dataset Details by Type and Id
=======================================

Fetch the dataset's XML by its ID within its dataset type, and deserialize the raw XML into JSON.

.. note:: Dataset type is specified by its short name; the short names of dataset types
   are available in the response to :doc:`list_all_dataset_types` request.

Request
-------
+------------+-------------------------------------------------------------------------------------+
| **Method** | **URI**                                                                             |
+============+=====================================================================================+
| GET        | http://<host>:<port>/secondary-analysis/datasets/{shortName}/{id}/details           |
+------------+-------------------------------------------------------------------------------------+

|

+----------------------+-----------------+----------------------------------+----------------+--------------------+---------------------------+
| **Path Parameters**  | **Data Type**   | **Description**                  | **Required**   | **Multi-valued**   | **Possible Values**       |
+======================+=================+==================================+================+====================+===========================+
| shortName            | string          | Short name of a dataset type;    | Yes            | No                 | | alignments              |
|                      |                 | list of all possible dataset     |                |                    | | barcodes                |
|                      |                 | types with their short names     |                |                    | | contigs                 |
|                      |                 | may be obtained through          |                |                    | | ccsalignments           |
|                      |                 | :doc:`list_all_dataset_types`    |                |                    | | ccsreads                |
|                      |                 | request; use values from         |                |                    | | hdfsubreads             |
|                      |                 | "shortName" fields of            |                |                    | | references              |
|                      |                 | Dataset Type objects.            |                |                    | | subreads                |
+----------------------+-----------------+----------------------------------+----------------+--------------------+---------------------------+
| id                   | integer         | Unique identifier of a dataset   | Yes            | No                 | | 4                       |
|                      |                 | within its dataset type; valid   |                |                    | | 12                      |
|                      |                 | values can be found in "id"      |                |                    | | 17                      |
|                      |                 | fields of objects returned by    |                |                    | | 133                     |
|                      |                 | :doc:`list_all_datasets_by_type` |                |                    |                           |
|                      |                 | request                          |                |                    |                           |
+----------------------+-----------------+----------------------------------+----------------+--------------------+---------------------------+

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
| Retrieve the deserialized XML of the dataset object of type 'references' with ID=12:
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/datasets/references/12/details
|
| Retrieve the deserialized XML of the dataset object of type 'subreads' with ID=17:
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/datasets/subreads/17/details

Response
--------
+-----------------------+-----------------------------------------------+--------------------------------------------------------------------------------------------+
| **HTTP Status Code**  | **Error Message**                             | **Description**                                                                            |
+=======================+===============================================+============================================================================================+
| 200 OK                | None                                          | Successful completion of the request.                                                      |
+-----------------------+-----------------------------------------------+--------------------------------------------------------------------------------------------+
| 404 Not Found         | The requested resource could not be found.    | Occurs when invalid value of shortName (non-existing dataset type) is passed               |
|                       |                                               | in the request. Note: in order to get the list of valid shortName values, use              |
|                       |                                               | :doc:`list_all_dataset_types` request.                                                     |
+-----------------------+-----------------------------------------------+--------------------------------------------------------------------------------------------+
| 404 Not Found         | Unable to find reference details              | Occurs when invalid value of ID (non-existing dataset identifier within its dataset type)  |
|                       | dataset '122'.                                | is passed in the request. Note: in order to get the list of valid ID values, use           |
|                       |                                               | :doc:`list_all_datasets_by_type` request.                                                  |
+-----------------------+-----------------------------------------------+--------------------------------------------------------------------------------------------+
| 406 Not Acceptable    | Resource representation is only available     | Occurs when invalid value of Accept header was passed in the request,                      |
|                       | with these Content-Types: application/json;   | for example: Accept: application/xml                                                       |
|                       | charset=UTF-8                                 |                                                                                            |
+-----------------------+-----------------------------------------------+--------------------------------------------------------------------------------------------+

|

+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| **Response Headers**          | **Description**                                                               | **Required**   | **Sample Value**                  |
+===============================+===============================================================================+================+===================================+
| Access-Control-Allow-Origin   | Specifying which web sites can participate in cross-origin resource sharing.  | Yes            | \*                                |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Date                          | The date and time that the message was sent.                                  | Yes            | Thu, 28 Jan 2016 00:23:26 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 10958                             |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+---------------------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**                           |
+==================+=====================================================================+
| application/json | See `Retrieve dataset details by type and id - response schema`_    |
+------------------+---------------------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `Retrieve dataset details by type and id - response sample`_

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


.. _`Retrieve dataset details by type and id - response schema`:

Retrieve dataset details by type and id - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. note:: The following schema definition is a JSON translation of the generic PacBioDataModel.xsd.

| Example 1: Schema for dataset details for an object of 'references' type:
|

.. literalinclude:: dataset_references_details_schema.json
    :language: javascript

| Example 2: Schema for dataset details for an object of 'subreads' type:
|

.. literalinclude:: dataset_subreads_details_schema.json
    :language: javascript


.. _`Retrieve dataset details by type and id - response sample`:

Retrieve dataset details by type and id - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Sample 1 - retrieve the deserialized XML of the dataset object of type 'references' with ID=12:
|

.. literalinclude:: get_datasets_references_12_details.json
    :language: javascript

|
| Sample 2 - retrieve the deserialized XML of the dataset object of type 'subreads' with ID=17:
|

.. literalinclude:: get_datasets_subreads_17_details.json
    :language: javascript


Get Dataset Schema by Type
==========================

Get schema definition of a specific dataset type.

.. note:: Dataset type is specified by its short name; the short names of dataset types
   are available in the response to :doc:`list_all_dataset_types` request.

Request
-------
+------------+---------------------------------------------------------------------------------------+
| **Method** | **URI**                                                                               |
+============+=======================================================================================+
| GET        | http://<host>:<port>/secondary-analysis/datasets/{shortName}/_schema                  |
+------------+---------------------------------------------------------------------------------------+

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
| Get schema definition of type 'alignments':
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/datasets/alignments/_schema
|
| Get schema definition of type 'barcodes':
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/datasets/barcodes/_schema
|
| Get schema definition of type 'contigs':
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/datasets/contigs/_schema
|
| Get schema definition of type 'ccsalignments':
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/datasets/ccsalignments/_schema
|
| Get schema definition of type 'ccsreads':
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/datasets/ccsreads/_schema
|
| Get schema definition of type 'hdfsubreads':
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/datasets/hdfsubreads/_schema
|
| Get schema definition of type 'references':
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/datasets/references/_schema
|
| Get schema definition of type 'subreads':
| GET http://smrtlink-alpha.nanofluidics.com:8081/secondary-analysis/datasets/subreads/_schema

Response
--------
+-----------------------+-----------------------------------------------+----------------------------------------------------------------------------------------+
| **HTTP Status Code**  | **Error Message**                             | **Description**                                                                        |
+=======================+===============================================+========================================================================================+
| 200 OK                | None                                          | Successful completion of the request.                                                  |
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
| Date                          | The date and time that the message was sent.                                  | Yes            | Wed, 27 Jan 2016 18:04:24 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 5188                              |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+-----------------------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**                             |
+==================+=======================================================================+
| application/json | | The response is JSON schema of a specific dataset type;             |
|                  | | see `Get dataset schema by type - response sample`_  for examples.  |
+------------------+-----------------------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `Get dataset schema by type - response sample`_

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


.. _`Get dataset schema by type - response sample`:

Get dataset schema by type - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Sample 1 - dataset schema of type 'alignments':
|

.. literalinclude:: alignment.schema.json
    :language: javascript

| Sample 2 - dataset schema of type 'barcodes':
|

.. literalinclude:: barcode.schema.json
    :language: javascript

| Sample 3 - dataset schema of type 'contigs':
|

.. literalinclude:: contigs.schema.json
    :language: javascript

| Sample 4 - dataset schema of type 'ccsalignments':
|

.. literalinclude:: ccs-alignment.schema.json
    :language: javascript

| Sample 5 - dataset schema of type 'ccsreads':
|

.. literalinclude:: ccs-read.schema.json
    :language: javascript

| Sample 6 - dataset schema of type 'hdfsubreads':
|

.. literalinclude:: subread.schema.json
    :language: javascript

| Sample 7 - dataset schema of type 'references':
|

.. literalinclude:: reference.schema.json
    :language: javascript

|
| Sample 8 - dataset schema of type 'subreads':
|

.. literalinclude:: subread.schema.json
    :language: javascript


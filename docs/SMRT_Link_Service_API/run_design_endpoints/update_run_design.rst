Update Run Design
=================

Update the record of a run design by ID.

Request
-------
+------------+-------------------------------------------+
| **Method** | **URI**                                   |
+============+===========================================+
| POST       | http://<host>:<port>/smrt-link/runs/{id}  |
+------------+-------------------------------------------+

|

+----------------------+-----------------+-----------------------+----------------+--------------------+---------------------+
| **Path Parameters**  | **Data Type**   | **Description**       | **Required**   | **Multi-valued**   | **Sample Value**    |
+======================+=================+=======================+================+====================+=====================+
| id                   | integer         | Unique identifier of  | Yes            | No                 | | 5                 |
|                      |                 | the run design        |                |                    | | 17                |
+----------------------+-----------------+-----------------------+----------------+--------------------+---------------------+

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
| application/json | See `Update run design - request schema`_              |
+------------------+--------------------------------------------------------+

Sample Request
~~~~~~~~~~~~~~
See `Update run design - request sample`_

Response
--------
+----------------------------+-----------------------------------------------+------------------------------------------------------------------------------+
| **HTTP Status Code**       | **Error Message**                             | **Description**                                                              |
+============================+===============================================+==============================================================================+
| 200 OK                     | None                                          | Successful completion of the request. The updated run design object          |
|                            |                                               | in a summary form will be returned in the response body.                     |
+----------------------------+-----------------------------------------------+------------------------------------------------------------------------------+
| 415 Unsupported Media Type | There was a problem with the request's        | Occurs when invalid value of Content-Type header was passed in the request,  |
|                            | Content-Type: Expected 'application/json'     | for example: Content-Type: application/x-www-form-urlencoded                 |
+----------------------------+-----------------------------------------------+------------------------------------------------------------------------------+
| 422: Unprocessable Entity  | XML did not conform to schema:                | Occurs when serialized XML file that was passed in "runDataModel" field      |
|                            | An error occurred unmarshalling the document  | does not conform to PacBioDataModel.xsd schema.                              |
+----------------------------+-----------------------------------------------+------------------------------------------------------------------------------+

|

+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| **Response Headers**          | **Description**                                                               | **Required**   | **Sample Value**                  |
+===============================+===============================================================================+================+===================================+
| Access-Control-Allow-Origin   | Specifying which web sites can participate in cross-origin resource sharing.  | Yes            | \*                                |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Date                          | The date and time that the message was sent.                                  | Yes            | Tue, 26 Jan 2016 17:24:02 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 175                               |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**              |
+==================+========================================================+
| application/json | See `Update run design - response schema`_             |
+------------------+--------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `Update run design - response sample`_

Additional info
---------------
+-----------------------------------+----------------------------------------------+
| **Authentication**                | **Technical Support**                        |
+===================================+==============================================+
| No authentication required.       | E-mail: support@pacb.com                     |
+-----------------------------------+----------------------------------------------+

Change Log
~~~~~~~~~~
+------------------+------------------------------------+--------------------------+
| **Release**      | **Description of changes**         | **Backward-compatible**  |
+==================+====================================+==========================+
| SMRT Link 3.0    | New service endpoint.              | N/A                      |
+------------------+------------------------------------+--------------------------+

.. _`Update run design - request schema`:

Update run design - request schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: run_design_update_schema.json
    :language: javascript

.. _`Update run design - request sample`:

Update run design - request sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Update the record of the run design with ID=1:
|
| POST http://smrtlink-alpha.nanofluidics.com:8081/smrt-link/runs/1
|

.. literalinclude:: update_run_design_request_sample.json
    :language: javascript

.. note:: Each of the fields in the request object is optional, and any fields that are present will be updated.

.. _`Update run design - response schema`:

Update run design - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: run_design_summary_schema.json
    :language: javascript

.. _`Update run design - response sample`:

Update run design - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: update_run_design_response_sample.json
    :language: javascript


Create Run Design
=================

Create a new run design.

Request
-------
+------------+-------------------------------------------+
| **Method** | **URI**                                   |
+============+===========================================+
| POST       | http://<host>:<port>/smrt-link/runs       |
+------------+-------------------------------------------+

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
| application/json | See `Create run design - request schema`_              |
+------------------+--------------------------------------------------------+

Sample Request
~~~~~~~~~~~~~~
See `Create run design - request sample`_

Response
--------
+----------------------------+-----------------------------------------------+------------------------------------------------------------------------------+
| **HTTP Status Code**       | **Error Message**                             | **Description**                                                              |
+============================+===============================================+==============================================================================+
| 201 Created                | None                                          | Successful completion of the request. The newly created run design object    |
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
| Date                          | The date and time that the message was sent.                                  | Yes            | Tue, 26 Jan 2016 01:13:12 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 176                               |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**              |
+==================+========================================================+
| application/json | See `Create run design - response schema`_             |
+------------------+--------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `Create run design - response sample`_

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

.. _`Create run design - request schema`:

Create run design - request schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: run_design_new_schema.json
    :language: javascript

.. _`Create run design - request sample`:

Create run design - request sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Create a new run design:
|
| POST http://smrtlink-alpha.nanofluidics.com:8081/smrt-link/runs
|

.. literalinclude:: create_run_design_request_sample.json
    :language: javascript

.. note:: The value of "name" field passed in the request body does not have to be unique.
   If a run design object with the same "name" already exists, the new run design object still
   will be created successfully, with a new "id" allocated for it by the server.

.. _`Create run design - response schema`:

Create run design - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: run_design_summary_schema.json
    :language: javascript

.. _`Create run design - response sample`:

Create run design - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: create_run_design_response_sample.json
    :language: javascript

.. note:: The "createdBy" and "createdAt" fields are automatically populated using the authenticated user's login
   and the current system time correspondingly. The initial "reserved" state is false.

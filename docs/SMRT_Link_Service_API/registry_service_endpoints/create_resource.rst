Create Resource
===============

Create a new resource.

Request
-------
+------------+-----------------------------------------------------------------+
| **Method** | **URI**                                                         |
+============+=================================================================+
| POST       | http://<host>:<port>/smrt-link/registry-service/resources       |
+------------+-----------------------------------------------------------------+

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
| application/json | See `Create resource - request schema`_                |
+------------------+--------------------------------------------------------+

Sample Request
~~~~~~~~~~~~~~
See `Create resource - request sample`_

Response
--------
+----------------------------+-----------------------------------------------+---------------------------------------------------------------------------------+
| **HTTP Status Code**       | **Error Message**                             | **Description**                                                                 |
+============================+===============================================+=================================================================================+
| 201 Created                | None                                          | Successful completion of the request. The newly created resource object         |
|                            |                                               | will be returned in the response body.                                          |
+----------------------------+-----------------------------------------------+---------------------------------------------------------------------------------+
| 400 Bad Request            | The request content was malformed: Object is  | Occurs when "resourceId" field was omitted or misspelled in the request body,   |
|                            | missing required member 'resourceId'.         | for example "resourceIdentifier" field was passed instead.                      |
+----------------------------+-----------------------------------------------+---------------------------------------------------------------------------------+
| 406 Not Acceptable         | Resource representation is only available     | Occurs when invalid value of Accept header was passed in the request,           |
|                            | with these Content-Types: application/json;   | for example: Accept: application/xml                                            |
|                            | charset=UTF-8                                 |                                                                                 |
+----------------------------+-----------------------------------------------+---------------------------------------------------------------------------------+
| 415 Unsupported Media Type | There was a problem with the request's        | Occurs when invalid value of Content-Type header was passed in the request,     |
|                            | Content-Type: Expected 'application/json'     | for example: Content-Type: application/x-www-form-urlencoded                    |
+----------------------------+-----------------------------------------------+---------------------------------------------------------------------------------+
| 422: Unprocessable Entity  | Resource with id 54031 already exists         | Occurs when a resource object with "resourceId" value passed in the request     |
|                            |                                               | already exists. Note: resource Id values that are already in use can be found   |
|                            |                                               | in "resourceId" fields of objects returned by :doc:`list_all_resources` request.|
+----------------------------+-----------------------------------------------+---------------------------------------------------------------------------------+

|

+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| **Response Headers**          | **Description**                                                               | **Required**   | **Sample Value**                  |
+===============================+===============================================================================+================+===================================+
| Access-Control-Allow-Origin   | Specifying which web sites can participate in cross-origin resource sharing.  | Yes            | \*                                |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Date                          | The date and time that the message was sent.                                  | Yes            | Fri, 26 Feb 2016 18:21:36 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 217                               |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**              |
+==================+========================================================+
| application/json | See `Create resource - response schema`_               |
+------------------+--------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `Create resource - response sample`_

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

.. _`Create resource - request schema`:

Create resource - request schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: resource_new_schema.json
    :language: javascript

.. _`Create resource - request sample`:

Create resource - request sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Create a new resource:
|
| POST http://smrtlink-alpha.nanofluidics.com:8081/smrt-link/registry-service/resources
|

.. literalinclude:: create_resource_request_sample.json
    :language: javascript

.. note:: User-defined resource Id passed in the request body must be unique.
   Resource Id values that are already in use can be found in "resourceId" fields
   of objects returned by :doc:`list_all_resources` request.

.. _`Create resource - response schema`:

Create resource - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: resource_object_schema.json
    :language: javascript

.. _`Create resource - response sample`:

Create resource - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: create_resource_response_sample.json
    :language: javascript

.. note:: The "uuid" field in the newly created resource object will be automatically populated by server-generated UUID,
   while "createdAt" and "updatedAt" fields will be automatically populated by the current system time.

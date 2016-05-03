Update Resource
===============

Update a resource object, identified by its server-provided UUID, with the new host and/or port values.

.. note:: Resource UUID values can be found in "uuid" fields of objects returned by :doc:`list_all_resources` request.

Request
-------
+------------+-------------------------------------------------------------------------------+
| **Method** | **URI**                                                                       |
+============+===============================================================================+
| POST       | http://<host>:<port>/smrt-link/registry-service/resources/{uuid}/update       |
+------------+-------------------------------------------------------------------------------+

|

+----------------------+-----------------+-----------------------------------+----------------+--------------------+----------------------------------------+
| **Path Parameters**  | **Data Type**   | **Description**                   | **Required**   | **Multi-valued**   | **Possible Values**                    |
+======================+=================+===================================+================+====================+========================================+
| uuid                 | string          | Server-provided UUID; valid UUID  | Yes            | No                 | | 7afeb98b-b63b-4d27-9d51-0d40744b3bbd |
|                      |                 | values can be found in "uuid"     |                |                    | | 1281c476-0e66-472f-971d-1268f18fc82e |
|                      |                 | fields of objects returned by     |                |                    |                                        |
|                      |                 | :doc:`list_all_resources` request.|                |                    |                                        |
+----------------------+-----------------+-----------------------------------+----------------+--------------------+----------------------------------------+

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
| application/json | See `Update resource - request schema`_                |
+------------------+--------------------------------------------------------+

Sample Request
~~~~~~~~~~~~~~
See `Update resource - request sample`_

Response
--------
+----------------------------+-----------------------------------------------+---------------------------------------------------------------------------------+
| **HTTP Status Code**       | **Error Message**                             | **Description**                                                                 |
+============================+===============================================+=================================================================================+
| 200 OK                     | None                                          | Successful completion of the request. The updated resource object               |
|                            |                                               | will be returned in the response body.                                          |
+----------------------------+-----------------------------------------------+---------------------------------------------------------------------------------+
| 400 Bad Request            | Request entity expected but not supplied      | Occurs when no request body was passed in the request. Note: passing empty      |
|                            |                                               | request body, i.e. {} only, is valid, but no request body is invalid.           |
+----------------------------+-----------------------------------------------+---------------------------------------------------------------------------------+
| 400 Bad Request            | The request content was malformed:            | Occurs when invalid data type was passed for one of fields in the request body, |
|                            | Expected Int as JsNumber, but got "8889"      | for example: passed "port" : "8889" (string), when port value shall be integer. |
+----------------------------+-----------------------------------------------+---------------------------------------------------------------------------------+
| 406 Not Acceptable         | Resource representation is only available     | Occurs when invalid value of Accept header was passed in the request,           |
|                            | with these Content-Types: application/json;   | for example: Accept: application/xml                                            |
|                            | charset=UTF-8                                 |                                                                                 |
+----------------------------+-----------------------------------------------+---------------------------------------------------------------------------------+
| 415 Unsupported Media Type | There was a problem with the request's        | Occurs when invalid value of Content-Type header was passed in the request,     |
|                            | Content-Type: Expected 'application/json'     | for example: Content-Type: application/x-www-form-urlencoded                    |
+----------------------------+-----------------------------------------------+---------------------------------------------------------------------------------+

|

+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| **Response Headers**          | **Description**                                                               | **Required**   | **Sample Value**                  |
+===============================+===============================================================================+================+===================================+
| Access-Control-Allow-Origin   | Specifying which web sites can participate in cross-origin resource sharing.  | Yes            | \*                                |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Date                          | The date and time that the message was sent.                                  | Yes            | Fri, 26 Feb 2016 22:19:18 GMT     |
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
| application/json | See `Update resource - response schema`_               |
+------------------+--------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `Update resource - response sample`_

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

.. _`Update resource - request schema`:

Update resource - request schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: resource_update_schema.json
    :language: javascript

.. _`Update resource - request sample`:

Update resource - request sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Update host and port values for the resource with UUID = 'be0f98db-85ce-48b4-bae4-0c60404b7498':
|
| POST http://smrtlink-alpha.nanofluidics.com:8081/smrt-link/registry-service/resources/be0f98db-85ce-48b4-bae4-0c60404b7498/update
|

.. literalinclude:: update_resource_request_sample.json
    :language: javascript

.. note:: The host and port values are both optional.

.. _`Update resource - response schema`:

Update resource - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: resource_object_schema.json
    :language: javascript

.. _`Update resource - response sample`:

Update resource - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: update_resource_response_sample.json
    :language: javascript

.. note:: "updatedAt" field in the updated resource object will be set to the current system time.


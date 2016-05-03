Create Health Gauge Message
===========================

Create a new health gauge message, and update the state of the corresponding health gauge.

Request
-------
+------------+------------------------------------------------------------------+
| **Method** | **URI**                                                          |
+============+==================================================================+
| POST       | http://<host>:<port>/smrt-base/health/gauges/{id}/messages       |
+------------+------------------------------------------------------------------+

|

+----------------------+-----------------+--------------------------------+----------------+--------------------+----------------------------------------+
| **Path Parameters**  | **Data Type**   | **Description**                | **Required**   | **Multi-valued**   | **Possible Values**                    |
+======================+=================+================================+================+====================+========================================+
| id                   | string          | Health gauge Id; valid Id      | Yes            | No                 | pacbio.health.gauges.disk_space_full   |
|                      |                 | values can be found in "id"    |                |                    |                                        |
|                      |                 | fields of objects returned by  |                |                    |                                        |
|                      |                 | :doc:`list_all_health_gauges`. |                |                    |                                        |
+----------------------+-----------------+--------------------------------+----------------+--------------------+----------------------------------------+

|

+---------------------+-----------------------------------------------------+--------------+-------------------+
| **Request Headers** | **Description**                                     | **Required** | **Sample Value**  |
+=====================+=====================================================+==============+===================+
| Content-Type        | The MIME type of the content in the request.        | Yes          | application/json  |
+---------------------+-----------------------------------------------------+--------------+-------------------+
| Accept              | Content-Types that are acceptable for the response. | Yes          | application/json  |
+---------------------+-----------------------------------------------------+--------------+-------------------+

|

+------------------+------------------------------------------------------------------+
| **Media Type**   | **Request Body Representation / Schema**                         |
+==================+==================================================================+
| application/json | See `Create health gauge message - request schema`_              |
+------------------+------------------------------------------------------------------+

Sample Request
~~~~~~~~~~~~~~
See `Create health gauge message - request sample`_

Response
--------
+----------------------------+-----------------------------------------------+------------------------------------------------------------------------------+
| **HTTP Status Code**       | **Error Message**                             | **Description**                                                              |
+============================+===============================================+==============================================================================+
| 201 Created                | None                                          | Successful completion of the request. The newly created health gauge         |
|                            |                                               | message object ill be returned in the response body.                         |
+----------------------------+-----------------------------------------------+------------------------------------------------------------------------------+
| 415 Unsupported Media Type | There was a problem with the request's        | Occurs when invalid value of Content-Type header was passed in the request,  |
|                            | Content-Type: Expected 'application/json'     | for example: Content-Type: application/x-www-form-urlencoded                 |
+----------------------------+-----------------------------------------------+------------------------------------------------------------------------------+

|

+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| **Response Headers**          | **Description**                                                               | **Required**   | **Sample Value**                  |
+===============================+===============================================================================+================+===================================+
| Access-Control-Allow-Origin   | Specifying which web sites can participate in cross-origin resource sharing.  | Yes            | \*                                |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Date                          | The date and time that the message was sent.                                  | Yes            | Fri, 01 Apr 2016 21:36:24 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 180                               |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+------------------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**                        |
+==================+==================================================================+
| application/json | See `Create health gauge message - response schema`_             |
+------------------+------------------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `Create health gauge message - response sample`_

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
| SMRT Base 3.0    | New service endpoint.              | N/A                      |
+------------------+------------------------------------+--------------------------+

.. _`Create health gauge message - request schema`:

Create health gauge message - request schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: health_gauge_message_new_schema.json
    :language: javascript

.. _`Create health gauge message - request sample`:

Create health gauge message - request sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Create a new health gauge message for the health gauge with Id = pacbio.health.gauges.disk_space_full, and update its state:
|
| POST http://smrtlink-alpha.nanofluidics.com:8081/smrt-base/health/gauges/pacbio.health.gauges.disk_space_full/messages
|

.. literalinclude:: create_health_gauge_message_request_sample.json
    :language: javascript

.. note:: The "message" and "severity" fields will be copied into "description" and "severity" fields of the health gauge
   correspondingly, thus updating its state.

.. _`Create health gauge message - response schema`:

Create health gauge message - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: health_gauge_message_object_schema.json
    :language: javascript

.. _`Create health gauge message - response sample`:

Create health gauge message - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: create_health_gauge_message_response_sample.json
    :language: javascript

.. note:: The "createdAt" and "uuid" fields are automatically populated with the current system time
   and a server-generated UUID correspondingly.


Fetch Health Gauge Messages
===========================

Fetch messages of the health gauge identified by its Id.

.. note:: Valid health gauge Id values can be found in "id" fields of objects returned by :doc:`list_all_health_gauges` request.

Request
-------
+------------+------------------------------------------------------------------------------------------+
| **Method** | **URI**                                                                                  |
+============+==========================================================================================+
| GET        | http://<host>:<port>/smrt-base/health/gauges/{id}/messages                               |
+------------+------------------------------------------------------------------------------------------+

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
| Fetch messages of the health gauge with Id = pacbio.health.gauges.disk_space_full:
| GET http://smrtlink-alpha.nanofluidics.com:8081/smrt-base/health/gauges/pacbio.health.gauges.disk_space_full/messages

Response
--------
+-----------------------+-----------------------------------------------+-----------------------------------------------------------------------------------------+
| **HTTP Status Code**  | **Error Message**                             | **Description**                                                                         |
+=======================+===============================================+=========================================================================================+
| 200 OK                | None                                          | Successful completion of the request.                                                   |
|                       |                                               | Note: if there is no health gauge corresponding to Id value passed in the request, then |
|                       |                                               | the response still will be 200 OK, with empty array of messages in the response body.   |
+-----------------------+-----------------------------------------------+-----------------------------------------------------------------------------------------+
| 406 Not Acceptable    | Resource representation is only available     | Occurs when invalid value of Accept header was passed in the request,                   |
|                       | with these Content-Types: application/json;   | for example: Accept: application/xml                                                    |
|                       | charset=UTF-8                                 |                                                                                         |
+-----------------------+-----------------------------------------------+-----------------------------------------------------------------------------------------+

|

+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| **Response Headers**          | **Description**                                                               | **Required**   | **Sample Value**                  |
+===============================+===============================================================================+================+===================================+
| Access-Control-Allow-Origin   | Specifying which web sites can participate in cross-origin resource sharing.  | Yes            | \*                                |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Date                          | The date and time that the message was sent.                                  | Yes            | Fri, 01 Apr 2016 21:40:22 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 182                               |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+------------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**                  |
+==================+============================================================+
| application/json | See `Fetch health gauge messages - response schema`_       |
+------------------+------------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `Fetch health gauge messages - response sample`_

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

.. _`Fetch health gauge messages - response schema`:

Fetch health gauge messages - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: health_gauge_messages_list_schema.json
    :language: javascript

.. _`Fetch health gauge messages - response sample`:

Fetch health gauge messages - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: fetch_health_gauge_messages_response_sample.json
    :language: javascript


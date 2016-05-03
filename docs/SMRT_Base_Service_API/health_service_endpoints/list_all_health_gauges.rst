List All Health Gauges
======================

Get a list of all health gauges.

Request
-------
+------------+----------------------------------------------------+
| **Method** | **URI**                                            |
+============+====================================================+
| GET        | http://<host>:<port>/smrt-base/health/gauges       |
+------------+----------------------------------------------------+

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
| Get a list of all health gauges:
| GET http://smrtlink-alpha.nanofluidics.com:8081/smrt-base/health/gauges

Response
--------
+-----------------------+-----------------------------------------------+--------------------------------------------------------------------------------+
| **HTTP Status Code**  | **Error Message**                             | **Description**                                                                |
+=======================+===============================================+================================================================================+
| 200 OK                | None                                          | Successful completion of the request.                                          |
|                       |                                               | Note: if there are no health gauges defined on the server, then the response   |
|                       |                                               | still will be 200 OK, with empty array of health gauges in the response body.  |
+-----------------------+-----------------------------------------------+--------------------------------------------------------------------------------+
| 406 Not Acceptable    | Resource representation is only available     | Occurs when invalid value of Accept header was passed in the request,          |
|                       | with these Content-Types: application/json;   | for example: Accept: application/xml                                           |
|                       | charset=UTF-8                                 |                                                                                |
+-----------------------+-----------------------------------------------+--------------------------------------------------------------------------------+

|

+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| **Response Headers**          | **Description**                                                               | **Required**   | **Sample Value**                  |
+===============================+===============================================================================+================+===================================+
| Access-Control-Allow-Origin   | Specifying which web sites can participate in cross-origin resource sharing.  | Yes            | \*                                |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Date                          | The date and time that the message was sent.                                  | Yes            | Fri, 01 Apr 2016 19:40:09 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 199                               |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**              |
+==================+========================================================+
| application/json | See `List all health gauges - response schema`_        |
+------------------+--------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `List all health gauges - response sample`_

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

.. _`List all health gauges - response schema`:

List all health gauges - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: health_gauges_list_schema.json
    :language: javascript

.. _`List all health gauges - response sample`:

List all health gauges - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: list_all_health_gauges_response_sample.json
    :language: javascript


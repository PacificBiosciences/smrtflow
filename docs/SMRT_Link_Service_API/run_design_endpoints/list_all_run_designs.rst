List All Run Designs
====================

Get a list of all run designs in summary form.

Request
-------
+------------+-------------------------------------------+
| **Method** | **URI**                                   |
+============+===========================================+
| GET        | http://<host>:<port>/smrt-link/runs       |
+------------+-------------------------------------------+

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
| Get a list of all run designs in summary form:
| GET http://smrtlink-alpha.nanofluidics.com:8081/smrt-link/runs

Response
--------
+-----------------------+-----------------------------------------------+------------------------------------------------------------------------------+
| **HTTP Status Code**  | **Error Message**                             | **Description**                                                              |
+=======================+===============================================+==============================================================================+
| 200 OK                | None                                          | Successful completion of the request.                                        |
|                       |                                               | Note: if there are no run designs defined on the server, then the response   |
|                       |                                               | still will be 200 OK, with empty array of run designs in the response body.  |
+-----------------------+-----------------------------------------------+------------------------------------------------------------------------------+
| 406 Not Acceptable    | Resource representation is only available     | Occurs when invalid value of Accept header was passed in the request,        |
|                       | with these Content-Types: application/json;   | for example: Accept: application/xml                                         |
|                       | charset=UTF-8                                 |                                                                              |
+-----------------------+-----------------------------------------------+------------------------------------------------------------------------------+

|

+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| **Response Headers**          | **Description**                                                               | **Required**   | **Sample Value**                  |
+===============================+===============================================================================+================+===================================+
| Access-Control-Allow-Origin   | Specifying which web sites can participate in cross-origin resource sharing.  | Yes            | \*                                |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Date                          | The date and time that the message was sent.                                  | Yes            | Mon, 25 Jan 2016 17:25:27 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 3215                              |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**              |
+==================+========================================================+
| application/json | See `List all run designs - response schema`_          |
+------------------+--------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `List all run designs - response sample`_

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

.. _`List all run designs - response schema`:

List all run designs - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: run_designs_list_schema.json
    :language: javascript

.. _`List all run designs - response sample`:

List all run designs - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: list_all_run_designs_response_sample.json
    :language: javascript


Delete Resource
===============

Delete a resource object identified by its server-provided UUID.

.. note:: Valid UUID values can be found in "uuid" fields of objects returned by :doc:`list_all_resources` request.

Request
-------
+------------+---------------------------------------------------------------------------+
| **Method** | **URI**                                                                   |
+============+===========================================================================+
| DELETE     | http://<host>:<port>/smrt-link/registry-service/resources/{uuid}          |
+------------+---------------------------------------------------------------------------+

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
| Accept              | Content-Types that are acceptable for the response. | Yes          | application/json  |
+---------------------+-----------------------------------------------------+--------------+-------------------+

|

+------------------+-----------------------------------------------------------+
| **Media Type**   | **Request Body Representation / Schema**                  |
+==================+===========================================================+
| application/json | Request schema is N/A since this is DELETE request        |
+------------------+-----------------------------------------------------------+

Sample Request
~~~~~~~~~~~~~~
| Delete the resource object with server-provided UUID = be0f98db-85ce-48b4-bae4-0c60404b7498:
| DELETE http://smrtlink-alpha.nanofluidics.com:8081/smrt-link/registry-service/resources/be0f98db-85ce-48b4-bae4-0c60404b7498

Response
--------
+-----------------------+-----------------------------------------------+----------------------------------------------------------------------------+
| **HTTP Status Code**  | **Error Message**                             | **Description**                                                            |
+=======================+===============================================+============================================================================+
| 200 OK                | None                                          | Successful completion of the request.                                      |
+-----------------------+-----------------------------------------------+----------------------------------------------------------------------------+
| 404 Not Found         | Unable to find resource                       | There are no resources corresponding to UUID value passed in the request.  |
|                       | be0f98db-85ce-48b4-bae4-0c60404b7498          | Note: valid UUID values can be found in "uuid" fields of                   |
|                       |                                               | objects returned by :doc:`list_all_resources` request. Note: if DELETE     |
|                       |                                               | request was sent twice for the same resource UUID, then the second time    |
|                       |                                               | the response is 404 Not Found, because the object doesn't exist anymore.   |
+-----------------------+-----------------------------------------------+----------------------------------------------------------------------------+
| 406 Not Acceptable    | Resource representation is only available     | Occurs when invalid value of Accept header was passed in the request,      |
|                       | with these Content-Types: application/json;   | for example: Accept: application/xml                                       |
|                       | charset=UTF-8                                 |                                                                            |
+-----------------------+-----------------------------------------------+----------------------------------------------------------------------------+

|

+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| **Response Headers**          | **Description**                                                               | **Required**   | **Sample Value**                  |
+===============================+===============================================================================+================+===================================+
| Access-Control-Allow-Origin   | Specifying which web sites can participate in cross-origin resource sharing.  | Yes            | \*                                |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Date                          | The date and time that the message was sent.                                  | Yes            | Sat, 27 Feb 2016 01:23:38 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 66                                |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**              |
+==================+========================================================+
| application/json | Response schema is N/A since this is DELETE request    |
+------------------+--------------------------------------------------------+

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


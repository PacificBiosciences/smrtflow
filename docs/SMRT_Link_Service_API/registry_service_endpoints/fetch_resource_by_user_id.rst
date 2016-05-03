Fetch Resource by Id
====================

Fetch the record of a resource object by its user-defined resource Id.

.. note:: Valid resource Id values can be found in "resourceId" fields of objects returned by :doc:`list_all_resources` request.

Request
-------
+------------+---------------------------------------------------------------------------------+
| **Method** | **URI**                                                                         |
+============+=================================================================================+
| GET        | http://<host>:<port>/smrt-link/registry-service/resources?resourceId={ID}       |
+------------+---------------------------------------------------------------------------------+

|

+----------------------+---------------+-------------------------------+--------------+------------------+-------------------+----------------------+
| **Query Parameters** | **Data Type** | **Description**               | **Required** | **Multi-valued** | **Default Value** | **Possible Values**  |
+======================+===============+===============================+==============+==================+===================+======================+
| resourceId           | string        | User-defined resource Id      | Yes          | No               | N/A               | my_resource_1, 54033 |
+----------------------+---------------+-------------------------------+--------------+------------------+-------------------+----------------------+

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
| Fetch the record of a resource object with user-defined resource Id = 5433:
| GET http://smrtlink-alpha.nanofluidics.com:8081/smrt-link/registry-service/resources?resourceId=54033

Response
--------
+-----------------------+-----------------------------------------------+----------------------------------------------------------------------------------------+
| **HTTP Status Code**  | **Error Message**                             | **Description**                                                                        |
+=======================+===============================================+========================================================================================+
| 200 OK                | None                                          | Successful completion of the request. Note: if there are no resources corresponding    |
|                       |                                               | to resource Id value passed in the request, then the response still will be 200 OK,    |
|                       |                                               | with empty array of resources in the response body. Valid resource Id values can be    |
|                       |                                               | found in "resourceId" fields of objects returned by :doc:`list_all_resources` request. |
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
| Date                          | The date and time that the message was sent.                                  | Yes            | Thu, 25 Feb 2016 22:52:35 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 219                               |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**              |
+==================+========================================================+
| application/json | See `Fetch resource by id - response schema`_          |
+------------------+--------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `Fetch resource by id - response sample`_

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

.. _`Fetch resource by id - response schema`:

Fetch resource by id - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. note:: The response will be resource array with one or zero elements.

.. literalinclude:: resource_by_user_id_schema.json
    :language: javascript

.. _`Fetch resource by id - response sample`:

Fetch resource by id - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: fetch_resource_by_user_id_response_sample.json
    :language: javascript


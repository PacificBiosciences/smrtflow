Search Run Designs
==================

Search for run designs, filtering by reserved state, creator, and/or summary substring.

Request
-------
+------------+------------------------------------------------------------------------------------------------------------------------+
| **Method** | **URI**                                                                                                                |
+============+========================================================================================================================+
| GET        | http://<host>:<port>/smrt-link/runs?reserved={RESERVED_STATE}&createdBy={CREATOR}&substring={SEARCH_SUMMARY_SUBSTRING} |
+------------+------------------------------------------------------------------------------------------------------------------------+

|

+----------------------+---------------+-------------------------------+--------------+------------------+-------------------+----------------------+
| **Query Parameters** | **Data Type** | **Description**               | **Required** | **Multi-valued** | **Default Value** | **Possible Values**  |
+======================+===============+===============================+==============+==================+===================+======================+
| reserved             | boolean       | Reserved state of run design  | No           | No               | false             | false, true          |
+----------------------+---------------+-------------------------------+--------------+------------------+-------------------+----------------------+
| createdBy            | string        | User login as defined in LDAP | No           | No               | N/A               | jdoe, tcruise        |
+----------------------+---------------+-------------------------------+--------------+------------------+-------------------+----------------------+
| substring            | string        | Run design summary substring  | No           | No               | N/A               | resequencing, lambda |
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
| Find all run designs which are reserved, created by user 'root', and have substring 'lambda' in summary:
| GET http://smrtlink-alpha.nanofluidics.com:8081/smrt-link/runs?reserved=true&createdBy=root&substring=lambda

Response
--------
+-----------------------+-----------------------------------------------+------------------------------------------------------------------------------------------+
| **HTTP Status Code**  | **Error Message**                             | **Description**                                                                          |
+=======================+===============================================+==========================================================================================+
| 200 OK                | None                                          | Successful completion of the request.                                                    |
|                       |                                               | Note: if there are no run designs corresponding to the filtering criteria, then          |
|                       |                                               | the response still will be 200 OK, with empty array of run designs in the response body. |
+-----------------------+-----------------------------------------------+------------------------------------------------------------------------------------------+
| 400 Bad Request       | The query parameter 'reserved' was malformed: | Occurs when invalid values of query parameters were passed, for example:                 |
|                       | 'kuku' is not a valid Boolean value           | GET http://smrtlink-alpha.nanofluidics.com:8081/smrt-link/runs?reserved=kuku             |
+-----------------------+-----------------------------------------------+------------------------------------------------------------------------------------------+

|

+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| **Response Headers**          | **Description**                                                               | **Required**   | **Sample Value**                  |
+===============================+===============================================================================+================+===================================+
| Access-Control-Allow-Origin   | Specifying which web sites can participate in cross-origin resource sharing.  | Yes            | \*                                |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Date                          | The date and time that the message was sent.                                  | Yes            | Fri, 20 Nov 2015 03:11:19 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 2728                              |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/json; charset=UTF-8   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**              |
+==================+========================================================+
| application/json | See `Search run designs - response schema`_            |
+------------------+--------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `Search run designs - response sample`_

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

.. _`Search run designs - response schema`:

Search run designs - response schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: run_designs_list_schema.json
    :language: javascript

.. _`Search run designs - response sample`:

Search run designs - response sample
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. literalinclude:: search_run_designs_response_sample.json
    :language: javascript


Redirect Request to Resource
============================

Send a proxy request to a resource identified by its server-provided UUID.

.. note:: Valid UUID values can be found in "uuid" fields of objects returned by :doc:`list_all_resources` request.

Request
-------
+------------+----------------------------------------------------------------------------------------------------------+
| **Method** | **URI**                                                                                                  |
+============+==========================================================================================================+
| | GET      | http://<host>:<port>/smrt-link/registry-service/resources/{uuid}/proxy/<path_to_resource>                |
| | POST     |                                                                                                          |
| | DELETE   |                                                                                                          |
+------------+----------------------------------------------------------------------------------------------------------+

Explanation
~~~~~~~~~~~

Given a resource like that:

.. literalinclude:: proxy_resource.json
    :language: javascript

any request sent to the endpoint:

/smrt-link/registry-service/resources/f9772193-21b9-4250-bbc8-9630086a2ba8/proxy

will be forwarded to this URL:

http://alpha2i.nanofluidics.com:8888/

What is supported in proxy request:

- All HTTP methods are supported, including POST with data.
- Also, path and query parameters may be attached to the request.
- Any headers, including authentication, will also be forwarded.

For example:

/smrt-link/registry-service/resources/f9772193-21b9-4250-bbc8-9630086a2ba8/proxy/path/to/resource?param=val

will be forwarded to

http://alpha2i.nanofluidics.com:8888/path/to/resource?param=val

.. note:: Since a resource is an instrument registered with SMRT Link, hence the request will be redirected to
   host:port of an instrument, which supports Instrument Web Services APIs. The list of supported endpoints
   is provided in `ICS API Table`_ below.

Path Parameters and Query Parameters
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Required path parameter is the resource's server-provided UUID, described in the table below.
| Other path parameters and/or query parameters depend on the request being redirected, and will be forwarded as they are.

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
| application/json | Depends on the request being redirected.               |
+------------------+--------------------------------------------------------+

Sample Request
~~~~~~~~~~~~~~
See `Redirect request to resource - requests and responses samples`_

Response
--------
+----------------------------+-----------------------------------------------+----------------------------------------------------------------------------+
| **HTTP Status Code**       | **Error Message**                             | **Description**                                                            |
+============================+===============================================+============================================================================+
| 200 OK                     | None                                          | Successful completion of the request.                                      |
+----------------------------+-----------------------------------------------+----------------------------------------------------------------------------+
| 404 Not Found              | Unable to find resource                       | There are no resources corresponding to UUID value passed in the request.  |
|                            | d3928320-2ba2-4486-ba5d-9737d7e44f19          | Note: valid UUID values can be found in "uuid" fields of                   |
|                            |                                               | objects returned by :doc:`list_all_resources` request.                     |
+----------------------------+-----------------------------------------------+----------------------------------------------------------------------------+
| 404 Not Found              | Response does not contain any data            | Occurs when resource <path_to_resource> specified in the proxy request     |
|                            |                                               | was not found.                                                             |
+----------------------------+-----------------------------------------------+----------------------------------------------------------------------------+
| 406 Not Acceptable         | Resource representation is only available     | Occurs when invalid value of Accept header was passed in the request,      |
|                            | with these Content-Types: application/json;   | for example: Accept: application/xml                                       |
|                            | charset=UTF-8                                 |                                                                            |
+----------------------------+-----------------------------------------------+----------------------------------------------------------------------------+
| 415 Unsupported Media Type | There was a problem with the request's        | Occurs when invalid value of Content-Type header was passed in             |
|                            | Content-Type: Expected 'application/json'     | the request, for example: Content-Type: application/x-www-form-urlencoded  |
+----------------------------+-----------------------------------------------+----------------------------------------------------------------------------+

|

.. note:: Other response status codes are possible, and they will be specific to the request being redirected.

|
|

+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| **Response Headers**          | **Description**                                                               | **Required**   | **Sample Value**                  |
+===============================+===============================================================================+================+===================================+
| Access-Control-Allow-Headers  | Specifying which HTTP headers are supported.                                  | Yes            | Origin, Content-Type, Accept      |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Access-Control-Allow-Methods  | Specifying which HTTP methods are supported.                                  | Yes            | GET, PUT, POST, DELETE, OPTIONS   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Access-Control-Allow-Origin   | Specifying which web sites can participate in cross-origin resource sharing.  | Yes            | \*                                |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Date                          | The date and time that the message was sent.                                  | Yes            | Mon, 29 Feb 2016 20:41:41 GMT     |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Length                | The length of the response body in octets.                                    | Yes            | 15018                             |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Content-Type                  | The MIME type of this content.                                                | Yes            | application/octet-stream          |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Server                        | A name for the server.                                                        | Yes            | spray-can/1.3.2                   |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+
| Keep-Alive                    | Control options for the current connection.                                   | Yes            | timeout=15,max=100                |
+-------------------------------+-------------------------------------------------------------------------------+----------------+-----------------------------------+

|

+------------------+--------------------------------------------------------+
| **Media Type**   | **Response Body Representation / Schema**              |
+==================+========================================================+
| application/json | Depends on the request being redirected.               |
+------------------+--------------------------------------------------------+

Sample Response
~~~~~~~~~~~~~~~
See `Redirect request to resource - requests and responses samples`_

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

.. _`ICS API Table`:

ICS API Table
~~~~~~~~~~~~~
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| **URI**                         | **Method**   | **Description**                                                                |
+=================================+==============+================================================================================+
| barcode/bindingkit              | POST         | provides a binding kit given a barcode                                         |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| barcode/prepkit                 | POST         | provides a template prep kit given a barcode                                   |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| barcode/sequencekit             | POST         | provides a sequencing kit given a barcode                                      |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| instrument/configuration        | GET          | returns the instrument configuration data                                      |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| instrument/configuration/name   | GET          | returns the instrument name (customer provided)                                |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| instrument/configuration/name   | POST         | sets the instrument name (customer provided)                                   |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| instrument/door/unlock          | POST         | unlocks the instrument door                                                    |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| instrument/failedtransfers      | GET          | returns array of acquisition ids that failed transfer                          |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| instrument/powerdown            | POST         | commands the instrument to power down                                          |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| instrument/retrytransfers       | POST         | resubmits collections that failed transfer                                     |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| instrument/state                | GET          | returns the fundamental instrument state                                       |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| instrument/sysalarms            | GET          | returns the instrument's current system alarms                                 |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| inventory                       | GET          | returns the instrument's inventory (triggers an inventory scan)                |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| run                             | GET          | returns the serialized run design for the currently loaded run                 |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| run                             | POST         | loads the provided run design                                                  |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| run                             | DELETE       | unloads the currently loaded run                                               |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| run/data                        | GET          | returns the run data for the currently loaded run                              |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| run/rqmts                       | GET          | returns the run requirements for the currently loaded run                      |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| run/stop                        | POST         | stops the current run                                                          |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| run/template                    | POST         | creates a run from the provide template                                        |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| runs                            | GET          | returns the list of available run designs                                      |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| runs/{id}                       | GET          | returns the run design for the provided id                                     |
+---------------------------------+--------------+--------------------------------------------------------------------------------+
| runs/{id}/load                  | POST         | loads the run for the provided id on the instrument and returns the run data   |
+---------------------------------+--------------+--------------------------------------------------------------------------------+

.. _`Redirect request to resource - requests and responses samples`:

Redirect request to resource - requests and responses samples
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Sample 1 - get the instrument's run:

GET http://smrtlink-alpha.nanofluidics.com:8081/smrt-link/registry-service/resources/f9772193-21b9-4250-bbc8-9630086a2ba8/proxy/run

Response:

.. literalinclude:: get_registry-service_resources_proxy_run.json
    :language: javascript

Sample 2 - get the instrument's run data:

GET http://smrtlink-alpha.nanofluidics.com:8081/smrt-link/registry-service/resources/f9772193-21b9-4250-bbc8-9630086a2ba8/proxy/run/data

Response:

.. literalinclude:: get_registry-service_resources_proxy_run_data.json
    :language: javascript

Sample 3 - get the instrument's configuration:

GET http://smrtlink-alpha.nanofluidics.com:8081/smrt-link/registry-service/resources/f9772193-21b9-4250-bbc8-9630086a2ba8/proxy/instrument/configuration

Response:

.. literalinclude:: get_registry-service_resources_proxy_instrument_configuration.json
    :language: javascript

Sample 4 - get the instrument's system alarms:

GET http://smrtlink-alpha.nanofluidics.com:8081/smrt-link/registry-service/resources/f9772193-21b9-4250-bbc8-9630086a2ba8/proxy/instrument/sysalarms

Response:

.. literalinclude:: get_registry-service_resources_proxy_instrument_sysalarms.json
    :language: javascript


Event/Message Server for Events and File Uploads
================================================

This WIP doc describe three pieces.

-  (for Context) How messages are generated within SL and define the
   general message datastructure
-  (for Context) How are messages sent out of SL to an external Service
-  What is the interface for the external Event Server that is consuming
   the messages
-  Starting up the SL event "listener" service

Events/Messages Generated Within SMRT Link Analysis Service
-----------------------------------------------------------

Internal Components (e.g., DataSet Service, JobManager Service) to SL
will send messages to a EventManagerActor. Each message has a standard
packet and *schema*.

Example (made terse as possible for demonstration purposes) and defined
as a *SMRT Link Message*

.. code:: javascript

    {
        "uuid": "83927d00-f46c-11e6-9f9b-3c15c2cc8f88",
        "createdAt": "2017-02-16T08:36:21.082-08:00",
        "eventTypeId": "smrtlink_job_change_state",
        "eventTypeVersion": 1,
        "message": {
            "jobId": 1234,
            "jobTypeId":
            "pbsmrtpipe",
            "state": "SUCCCESSFUL"
        }
    }

-  *eventTypeId* must map to a well defined schema defined in *message*
   which should be documented. When the model changes, the id must
   change. One possible way of doing this is to ``job_change_state2`` to
   encode the version.
-  *eventTypeVersion* Version of eventTypeId message schema
-  *createdAt* ISO8601 encoded version of the datetime the original
   message was created
-  *message* message payload
-  *uuid* Globally unique identifier for message. Assigned by the
   creator of the message

Internally at the EventManagerActor, the messages will be augmented with
the SL context information, such as the SL globally unique identifier
(TODO: How is this determined and assigned? For demonstration purposes a
UUID will be used. A URL of the SL instance is actually more useful, but
is leaking customer information)

Defining this data model as a *SMRT Link System Message*

.. code:: javascript

    {
        "smrtlinkId": "2319db24-f46e-11e6-a35c-3c15c2cc8f88",
        "uuid": "83927d00-f46c-11e6-9f9b-3c15c2cc8f88",
        "createdAt": "2017-02-16T08:36:21.082-08:00",
        "eventTypeId": "smrtlink_job_change_state",
        "eventTypeVersion": 1,
        "message": {
            "jobId": 1234,
            "jobTypeId": "pbsmrtpipe",
            "state": "SUCCCESSFUL"
        }
    }

How Messages are sent out of SMRT Link to External Server
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The EventManagerActor will forward messages to the listeners (i.e.,
Actors) that can take actions, such as sending an email on job failure,
make POST requests to External Server, or create jobs for
"auto-analysis".

Filtering of messages that are sent to External Servers should be
handled by configuration. In other words, it should be configurable to
only send *smrtlink\_job\_change\_state* messages, or only
*eula\_signed* event/message types.

SMRT Event "Listener" Services
------------------------------

(Not discussed here: Authentication and Authorization, or token
services, token refresh)

Status of Server (same as SL)

::

    GET  /status

Create an Event/Message

::

    POST /api/v1/events # with a SMRT Link System Message as the body

(TODO) Get an Event/Message

::

    GET /api/v1/events/{UUID}

(TODO) Get Events from a SMRT Link System by System UUID

::

    GET /api/v1/events/smrt-link/{UUID}

This starts to couple to the backend storage layer (e.g., elasticsearch,
cassandra). This backend layer should probably be used directly for more
advanced queries.

(TODO) Upload tar.gz or zip'ed dirs (e.g., Install Failure)

::

    POST /api/v1/files

TODO: Define requirements on max file size that is supported. TODO:
Define standard "schema" for zipped dirs. For example, every zipped
archive must have a manifest.json with the included files and identifer
for that type of "file" upload. This will enable well defined parsing on
the consumer side. For example, a failed install would have a different
identifier that an analysis job failure (Analogous to the eventTypeId
which maps to a specific schema).


File Upload Example
~~~~~~~~~~~~~~~~~~~

Only tgz or tar.gz files are supported. Files are restricted to be less than 8MB (configurable via spray webframework). There must be
a tech-support-manifest.json file in root directory. This manifest will provide metadata about the SL system (e.g., SL System version).

::

    curl -v -F techsupport_tgz=@/Users/mkocher/repos/smrtflow/example.tgz http://localhost:8070/api/v1/files


Starting up the SMRT Event Listener Services
--------------------------------------------

Requires java 1.8 and sbt

For demo and testing purposes the system is configured to write the events to a directory.

Optional custom configure of the port and the port to start on:

::

    export SMRTFLOW_SERVER_PORT=8888
    export SMRTFLOW_EVENT_ROOT_DIR=/path/to/

Start the Server


    sbt run-main com.pacbio.secondary.smrtlink.app.SmrtEventServerApp


Using the EventServer Client
----------------------------


See *EventServerClient* in *com.pacbio.secondary.smrtlink.client* for details.
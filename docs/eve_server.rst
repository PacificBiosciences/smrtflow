Eve SMRT Server for Events and Tech Support File Uploads
========================================================

General Overview
----------------

- System Overview
- Building, Configuring and Starting up Eve
- Eve Services
- TechSupport TGZ bundle format
- (for Context) How messages are generated within SL and define the general message datastructure
- (for Context) How are messages sent out of SL to an external Service


System Overview
---------------

Eve is a SMRT Server instance that enables SMRT Link to send Events and TechSupport TGZ file uploads.

Events that are sent to Eve are written to disk and imported into ElasticSearch and are accessible from Kibana.

There are three main components:

- Eve SMRT Server
    - Staging    https://smrtlink-eve-staging.pacbcloud.com:8083
    - Production https://smrtlink-eve.pacbcloud.com:8083
- ElasticSearch 2.4.x
- UI Kibana 4.6
    - Staging    http://smrtfleet-kibana-staging.nanofluidics.com
    - Production http://smrtfleet-kibana.nanofluidics.com


Building, Configuring and Starting up Eve
-----------------------------------------

Requires java 1.8 and sbt

Build
-----

::

    $> sbt smrt-server-link/{compile,pack}


Generates exes in *smrt-server-link/target/pack/bin*. Specifically, *smrt-server-eve*

For demo and testing purposes the system is configured to write the events to a directory.

Optional custom configure of the port and the port to start on:

Set the port. **By convention and to standardize, it is STRONGLY recommended to be set to 8083**

::

    export PB_SERVICES_PORT=8083


Set the location where Eve will write files to

::

    export EVE_ROOT_DIR=/path/to/where/files/are/written


The output files are written in the form:

Tech Support TGZ Uploaded bundles are unzipped next to the companion *.tar.gz* file.

Note, the UUID of the uploaded bundle is NOT the UUID of the tech support bundle. In other words, a TGZ Tech Support bundle can be uploaded multiple times.

::

    <EVE_ROOT_DIR>/files/<YEAR>/<MONTH><DAY>/<UPLOADED_BUNDLE_UUID>
    <EVE_ROOT_DIR>/files/<YEAR>/<MONTH><DAY>/<UPLOADED_BUNDLE_UUID>.tar.gz


Events are written as

::

    <EVE_ROOT_DIR>/<SMRT-LINK-SYSTEM-UUID>/<EVENT-UUID>.json

Other Config

::

    export SMRTFLOW_EVENT_API_SECRET="rick" # this must be consistent with the client used in SL


See the application.conf and reference.conf for all configuration options accessible from Eve.

Start the Server
----------------

::

    $> smrt-server-link/target/pack/bin/smrt-server-eve

Command line args

::

    --log-file=/path/to/log.file
    --log-level=DEBUG

Note, there is no support for *--help*

Eve and SMRT Link Server Tools
------------------------------

Building Tools

::

    $>sbt smrt-server-link/{compile,pack}


Generates tools, such as *pbservice*, *tech-support-bundler* and *tech-support-uploader*. See the *--help* in each tool for details.

Summary of Usage:

Uploading TGZ bundles to Eve, is performed by *tech-support-uploader*

::

    $> tech-support-uploader /path/to/ts-bundle.tar.gz  --url http://localhost:8083

When using from SMRT Link build, the default Eve URL should be set from smrtlink-system-config.json



For triggering Tech Support System Status from SMRT Link, use *pbservice*


::

    $> pbservice ts-status --user=rick --comment="Test Bundle creation" --host=smrtlink-bihourly --port=8081


This will create a tech-support system status TGZ bundle and upload to Eve. If the SMRT Link system is not configured with a Eve URL, the job creation step will fail.

Similarly, for requesting a TechSupport Failed Job 1234

::

    $> pbservice ts-failed-job 1234 --user=mkocher --comment="Test Failed Job"

Note, if the job is not in a failed state, or the job does not exist, there should be an error message and *pbservice* will return with a non-zero exit code.

Tech Support TGZ Bundle
-----------------------

The Tech Support TGZ bundle is a tar.gz file that contains a *tech-support-manifest.json* metadata file as well as any files, such as log or config files included in the bundle.

The bundle manifest defines the "type" of bundle and the schema of *REQUIRED* files and directory structure to be included in the bundle.

The two main bundles are

1. SMRT Link System Status (or failed installs)
2. SMRT Link Failed Job


Example manifest JSON for the SMRT Link System Status

.. code-block:: javascript


    {
        "bundleTypeVersion": 1,
        "bundleTypeId": "sl_ts_bundle_system_status",
        "id": "cef996da-bf7c-4cec-b983-af4e95486ca6",
        "comment": "Created by smrtflow version 0.6.7+755.92d16d8",
        "smrtLinkSystemVersion": "5.0.0.SNAPSHOT4888",
        "dnsName": "smrtlink-bihourly.nanofluidics.com",
        "createdAt": "2017-05-25T11:10:56.749-07:00",
        "user": "mkocher",
        "smrtLinkSystemId": "a0a2702a-cb7a-3a63-ac5f-fad696425a04"
    }


Note, that when a Tech Support TGZ bundle is uploaded into Eve, an "uploaded" Event with the TS Manifest metadata will be created. This Event will also will have the path to the unzipped TechSupport Bundle.

All tools *MUST* use the Event interface to look for recently uploaded TechSupport TGZ bundles.

**DO NOT USE THE DIRECT ACCESS TO FILE SYSTEM** This can change and is not the public interface to Eve. It's a configuration parameter and the output destination can change.

Eve WebServices
---------------

See the */smrt-server-link/src/main/resources/eventserver_swagger.json* or "<HOST>:<PORT>/api/v1/swagger"for details of the WebServices.

Use the swagger UI to get a prettified view of the swagger JSON file



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
   change. One possible way of doing this is to *sl_job_change_state2* to
   encode the version. The eventTypeId should be prefixed with **sl_**
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
        "dnsName: "my-host",
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

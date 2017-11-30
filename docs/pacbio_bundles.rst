PacBio Data Bundle Model and Services
=====================================

A PacBio Data Model is a manifest.xml with a directory for resources, such as config files, or resources used by applications within SMRT Link (and SAT applications), ICS and Primary Analysis.


Requirements
~~~~~~~~~~~~

-  Contain parameter and configuration files from ICS, PA, SAT, and SL Services
-  OS independent, standard  vanilla .tgz format
-  Each PacBio component (ICS, SL, SAT, DEP) can own sub-components within the bundle
   and define the schemas as they see fit
-  Each single bundle represents a coherent grouping of config/parameter files that are intended to work across all components of the system.


Example PacBio Data Bundle
~~~~~~~~~~~~~~~~~~~~~~~~~~

The PacBio Data Bundle is a general file format that can be used in several different usecases. For example, an extension of the PipelineTemplate, View Rule, Report Rules Data Bundle in SMRT Link, and PacBioTestData Bundle (TODO).

The most important bundle is the "Chemistry" Bundle. This PacBio Data Bundle type that contains ICS, SAT related files to be used
from SL and SL services is provided here http://bitbucket.nanofluidics.com:7990/projects/SL/repos/chemistry-data-bundle/browse

Example PacBio Data Bundle manifest.xml

::

    <?xml version='1.0' encoding='utf-8'?>
    <Manifest>
      <Package>chemistry</Package>
      <Version>4.0.0</Version>
      <Created>11/28/16 11:16:46 PM</Created>
      <Author>build</Author>
    </Manifest>



Note, **the version must be provided using the Semantic Version scheme. This ensures a clear, well-defined model for ordering and comparing bundles versions.**


PacBio Data Bundle Model
~~~~~~~~~~~~~~~~~~~~~~~~

This model contains metadata about the bundle.

-  type {String} Bundle type id (e.g., "chemistry")
-  version: {String} SemVer of the bundle. Unique Identifier to bundle
   resource within a bundle type. The bundle version comparision will
   be performed following the semver spec.
-  importedAt: {DateTime} When the bundle was imported at
-  isActive: {Boolean} If the bundle is active (For a given bundle type, only one bundle can be active)
-  url: {URL} Download Link URL for file(s) (as .tgz?) served from SL Services



SMRT Bundle Server
~~~~~~~~~~~~~~~~~~

.. warning:: As of 5.1.0, the chemistry bundle type id has been changed to **chemistry-pb**

.. warning:: As of 5.1.0, the SMRT Link Bundle services and the Bundle Upgrade have diverged. Please see the SMRT Link Server docs for details on the bundle related services in SMRT Link Server.

These services are for the **stand-alone Chemistry Parameter Update Bundle server**.

The endpoints are documented in the swagger file (**bundleserver_swagger.json**) within the project, or the **/api/v2/swagger** endpoint of the services.

The swagger-UI can be used to visualize the endpoint APIs. http://swagger.io/swagger-ui/

Servers
~~~~~~~

- http://smrtlink-update-staging.pacbcloud.com:8084
- http://smrtlink-update.pacbcloud.com:8084

Status Staging Server

::

    $> http get http://smrtlink-update-staging.pacbcloud.com:8084/status -b
    {
        "id": "bundle-server",
        "message": "Services have been up for 112 hours, 21 minutes and 30.557 seconds.",
        "uptime": 404490557,
        "user": "root",
        "uuid": "66fb205f-2599-3a37-919e-a0dc5552fee0",
        "version": "0.6.7+5475.e2b6df3"
    }

Status

::

    $> http get http://smrtlink-update.pacbcloud.com:8084/status -b
    {
        "id": "bundle-server",
        "message": "Services have been up for 124 hours, 48 minutes and 10.517 seconds.",
        "uptime": 449290517,
        "user": "root",
        "uuid": "56b814db-f0ef-3b91-880b-d1855545b3f8",
        "version": "0.6.7+2.82f4bc1"
    }




Legacy API
~~~~~~~~~~

.. note:: This should only be used for PacBio System Release version "5.0.0".

List bundles

::

    $> http get http://smrtlink-update-staging.pacbcloud.com:8084/smrt-link/bundles -b
    [
        {
            "createdBy": "integration team",
            "importedAt": "2017-06-08T20:48:14.322Z",
            "isActive": false,
            "typeId": "chemistry",
            "version": "9.9.9"
        },
        {
            "createdBy": "build",
            "importedAt": "2017-06-08T21:40:04.475Z",
            "isActive": true,
            "typeId": "chemistry",
            "version": "5.0.0+00c49de"
        }
    ]



Get a Specific bundle resource

::

    GET /smrt-link/bundles/{bundle-type-id}/{bundle-version} # Bundle Resource or 404

Example:

::

    GET /smrt-link/bundles/chemistry/1.2.3+3.ebbde5

Download a PacBio Data Bundle

::

    GET /smrt-link/bundles/{bundle-type-id}/download


New


Building a Stand Alone Chemistry Update Bundle Server
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Get repo: http://bitbucket.nanofluidics.com:7990/projects/SL/repos/smrtflow/browse


::

    $> sbt smrt-server-bundle/{compile,pack}


Generates the Server Exe **smrt-server-bundle/target/pack/bin/smrt-server-data-bundle**


Configuration
~~~~~~~~~~~~~

The configuration for SMRT Link or the stand-alone Chemistry Data is performed in the same way.

**For running a stand alone chemistry bundler server, it is strongly recommended for consistency to standardize on port 8084**

::

    $> export PB_SERVICES_PORT=8084


Configure the root bundle path

::

    $> export SMRTFLOW_BUNDLE_DIR=/path/to/pacbio-bundles


Or by setting the *smrtflow.server.bundleDir* key in the smrtlink-system-config.json (if running from SMRT Link Server).


Details of the Root Bundle Dir
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

When the bundle server is started up, the system will load bundles within subdirectories named with the **PacBio System Release Version** of root directory. The subdirectories must be valid semver format and contain a list of valid bundles.

A valid bundle is a bundle that has a unzipped companion with the name as `BUNDLE-ID`-`BUNDLE-VERSION` directory with an unzipped companion of the same name ``BUNDLE-ID`-`BUNDLE-VERSION.tar.gz`.


This will yield `<ROOT-BUNDLE-DIR>/<PACBIO-SYSTEM-VERSION>/<BUNDLE-ID>-<BUNDLE-VERSION>` and  `<ROOT-BUNDLE-DIR>/<PACBIO-SYSTEM-VERSION>/<BUNDLE-ID>-<BUNDLE-VERSION>.tar.gz` format.

**ALL BUNDLES within a specific `PACBIO-SYSTEM-VERSION` must be compatible with the companion version SMRT Link.**

Example directory structure

For a **PacBio System Release Version** `7.0.0` in root bundle dir `/path/to/bundles-root`, the directory structure could be:

::

    $> mkocher@login14-biofx01:pacbio-bundles$ ls -la /path/to/bundles-root/7.0.0
    total 112
    drwxar-xr-x 4 secondarytest Domain Users  4096 May 31 18:04 .
    drwxr-xr-x 6 secondarytest Domain Users  4096 May 31 15:40 ..
    drwxr-xr-x 6 secondarytest Domain Users  4096 May 31 18:04 chemistry-4.1.0
    -rw-r--r-- 1 secondarytest Domain Users 42269 May 31 18:04 chemistry-4.1.0.tar.gz
    drwxr-xr-x 6 secondarytest Domain Users  4096 May 31 15:40 chemistry-5.0.0
    -rwxr-xr-x 1 secondarytest Domain Users 38566 May 31 15:40 chemistry-5.0.0.tar.gz
    -rwxr-xr-x 1 secondarytest Domain Users  1168 May 31 15:40 README.md


.. warning:: For loading 5.0.0 bundles to be used in the **Legacy** V1 API, use the "5.0.0". These bundles will now be available at the legacy routes as well as the V2 API.


Building and Starting up the Chemistry Bundle Upgrade Server
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


::

    $> smrt-server-link/target/pack/bin/smrt-server-data-bundle

Command line args

::

    --log-file=/path/to/log.file
    --log-level=DEBUG

Note, there is no support for *--help*

The log file will log the loaded and "active" data bundles on startup.

Getting a List of PacBio Data Bundles from SMRT Link Server
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. warning:: This approach will NOT work for the Bundle Server

Use **pbservice** to get a list of bundles on the SMRT Link server.

::

    $> smrt-server-link/target/pack/bin/pbservice get-bundles --host=smrtlink-bihourly --port=8081
    Bundle Id Version Imported At              Is Active
    chemistry 5.0.0   2017-06-01T01:04:09.885Z true
    chemistry 4.1.0   2017-06-01T01:04:15.121Z false
    chemistry 4.1.0   2017-06-01T01:04:15.130Z false

The **pbservice** exe will be built from **sbt smrt-server-link/{compile,pack}** command.

Bundles Stored within the SL System install
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  All PacBio Data bundles are stored with SMRT Link pbbundler. The default chemistry bundle is packaged within pbbundler SL package.
- The default chemistry bundle is packaged within pbbundler SL package and is pulled from http://bitbucket.nanofluidics.com:7990/projects/SL/repos/chemistry-data-bundle/browse


Chemistry Data Bundle Details
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The "Chemistry" bundle is the core PacBio data model that contains information related to chemistry parameters and configuration for SMRT Link, ICS, PA and tools from secondary analysis (i.e.,SAT)


SMRT Link PartNumbers and Automation Constraints WebService
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The ``definitions/PacBioAutomationConstraints.xml`` is loaded from most
recent chemistry bundle. This is translated from XML (via jaxb) and
exposed as JSON as a webservice. This service will be used by the
RunDesign and SampleSetup UI application in SL.

::

    GET /smrt-link/automation-constraints # Returns a single PacBioAutomationConstraints JSON response

Note, if there is not a chemistry bundle loaded, the response will
return a 404.



SMRT Link Periodic Checking for Chemistry Data Bundle Upgrades
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

SMRT Link Services are configured to check the configured Chemistry Bundle Upgrade services (if the URL is configured in the `smrtlink-system-config.json`) every 12 hrs. The check to the external server for "newer" Chemistry Parameter bundles based on the semantic version scheme. See http://semver.org/ for details.

Using the nested naming format in the JSON file, the `smrtflow.server.chemistryBundleURL` has type `Option[URL]`. The URL is the base url of the external bundle service. For example, `http://my-server/smrt-link/bundles`. This external endpoint will poll the external server every day for newer chemistry bundles.

If a newer "Chemistry" Data Bundle is detected it will be downloaded and added to the chemistry bundle registry and exposed at `smrt-link/bundles/chemistry`. Note, it will only be added to the registry, it **will not be activated** when the bundle is downloaded.

Activation must be done via an explicit call to the services to activate the PacBio Chemistry Data Bundle. See the swagger file or endpoint for details on the WebService calls.



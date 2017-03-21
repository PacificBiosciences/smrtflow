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
from SL and SL services is provided here http://bitbucket.nanofluidics.com:7990/users/mkocher/repos/chemistry-bundle/browse

Example manifest.xml

::

    <?xml version='1.0' encoding='utf-8'?>
    <Manifest>
      <Package>chemistry</Package>
      <Version>4.0.0</Version>
      <Created>11/28/16 11:16:46 PM</Created>
      <Author>build</Author>
    </Manifest>



Note, **the version must be provided using the Semantic Version scheme**. This ensures a clear, well-defined model for ordering and comparing bundles versions.


SMRT Link Bundle Services
~~~~~~~~~~~~~~~~~~~~~~~~~

SMRT Link Web Service Bundle Interface

Get a List of All SL Bundles

::

    GET /smrt-link/bundles # Returns a list of Bundle Resource instances

Get a List of SL Bundle by bundle type id (e.g., "chemistry")

::

    GET /smrt-link/bundles/{bundle-type-id} # Returns a list of Bundle instances of type 'bundle-type-id'

PacBio Data Bundle Model:

This model contains metadata about the bundle.

-  type {String} Bundle type id (e.g., "chemistry")
-  version: {String} SemVer of the bundle. Unique Identifier to bundle
   resource within a bundle type. The bundle version comparision will
   be performed following the semver spec.
-  importedAt: {DateTime} When the bundle was imported at
-  isActive: {Boolean} If the bundle is active (For a given bundle type, only one bundle can be active)
-  url: {URL} Download Link URL for file(s) (as .tgz?) served from SL Services

Get a Specific bundle resource

::

    GET /smrt-link/bundles/{bundle-type-id}/{bundle-version} # Bundle Resource or 404

Example:

::

    GET /smrt-link/bundles/chemistry/1.2.3+3.ebbde5

Adding a new Bundle

::

    POST /smrt-link/bundles {"url": <bundle-url>} # returns Bundle Resource

Only URL schemes, "file" and "http" are supported with tar.gz or tgz. For "file", the file
will be copied locally into SL System. For "http", the file will be
downloaded into SL System location. Note, there is not auth model for pulling from the URL (i.e., not token config or basic auth).
Aside from .tgz and .tag.gz, git repos are also supported.

Example:

::

    POST /smrt-link/bundles {"url": "http://my-domain.com/bundles/chemistry-1.2.3+3.ebbdde4.tgz""}


Fetching a Git repo with a pbpipeline bundle (pipeline templates and view rules, etc...):

::

    POST /smrt-link/bundles {"url": "https://github.com/PacificBiosciences/pbpipeline-helloworld-resources.git""}



Checking for an upgraded bundle version.

::

    GET /smrt-link/bundles/{bundle-type-id}/upgrade


Returns

::

    { "bundle": Option[PacBioBundle]}

If an upgrade is available it will return a newer version (based on the semantic version spec). If no bundle is returned, there isn't a newer bundle.


Upgrading a Bundle

::

    POST /smrt-link/bundles/{bundle-type-id}/{bundle-version/upgrade


This will mark all other bundle types with `bundle-type-id` to in active and mark bundle with version `bundle-version` as active.

Returns


::

    {"bundle": PacBioBundle}


The bundle will have *isActive* with `true` if the bundle was successfully upgraded.

Bundles Stored within the SL System install
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  All PacBio Data bundles are stored with SMRT Link pbbundler. The default chemistry bundle is packaged within pbbundler SL package.
- {DEP} Add bundleDir to `smrtlink-system-config.json`
- {SL} Add configuration to `smrtlink-system-conf.json` to provide
   a configurable path via "smrtflow.server.bundleDir. This must be read+write by the user that launched the services.
- TODO The default chemistry bundle is packaged within pbbundler SL package. http://mkocher@bitbucket.nanofluidics.com:7990/scm/~mkocher/chemistry-bundle.git


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


Explicit Dependencies of PacBio Components on Chemistry Bundle Resources
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  SAT and DEP

    - Tools in smrtcmds/bin have setup ENV var to expose an add-on or replacement registry from <SMRT_LINK_BUNDLE_DIR>/chemistry-latest
    - Secondary Analysis tools can extend the pre-canned registry of config/param files by loading from the <SMRT_LINK_BUNDLE_DIR>/chemistry-latest

-  SMRT Link Services

   -  Exposes general PacBio Data Bundle webservice. This can be used for extend secondary-analysis pipelines, "Chemistry" bundles, or PacBio Test Data bundles (used in secondary analysis)
   -  On startup, SMRT Link Service load the most recent Chemistry Version from the bundle directory and load the
      PacBioAutomationConstraints.xml to SL UI RunDesign and Sample Setup
   -  *Requires* the default chemistry bundle to be included at build (and run) time.
   -  **TODO** An external URL can be configured to look for "Chemistry" Data Bundle updates. If a newer bundle version is detected, the bundle will be downloaded (but NOT marked as active). Once the bundle is downloaded and exposed in the registry, it can be marked as active.
   - When a Chemistry bundle is upgrades, a new softlink is created to the bundle in <SMRT_LINK_BUNDLE_DIR/chemistry-latest. When a new pipeline or commandline tool from <SMRT_ROOT>/smrtcmds/bin is run, this tool will have access to the newer chemistry version.

-  SMRT Link UI:

   -  Run Design and Sample Setup Apps required the data from
      PacBioAutomation Constraints exposed as a webservice from SMRT
      Link. This data is loaded from the most recent (activated) chemistry bundle

-  ICS:

    - Look to SMRT Link for newer Chemistry Bundles
    - Downloads newer bundles from SL
    - Installs/Activates bundles from Instrument UI

SL exporting Chemistry Bundle Interface for Pipeline Tools
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Before running an analysis job (i.e., pbsmrtpipe job) or any other
Service Job, the absolute path to the chemistry bundle dir is exported
as:

::

    export PB_CHEMISTRY_BUNDLE_DIR=/path/to/chemistry-bundle

Tools within SAT should (override, or augment?) the default loaded
chemistry configuration.

.. note:: This needs to be addressed by DEP to load the most recent chemistry bundle and export the necessary ENV var before executing any commands from "smrtcmds/bin".


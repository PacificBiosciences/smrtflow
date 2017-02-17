Example Chemistry Bundle
========================

Example Chemistry bundle that contains ICS, SAT related files to be used
from SL and SL services (e.g., https://github.com/PacificBiosciences/pbpipeline-helloworld-resources)

Requirements
~~~~~~~~~~~~

-  Contain chemistry related configuration files from ICS and SAT
-  OS independent (SL doesn't has a requirement that it can run on many
   linux distros, in other words, we can't use deb or rpms)
-  Each team (ICS, SL, SAT, DEP) can own subcomponents within the bundle
   and define the schemas as they see fit
-  When possible, share common config files and schemas

Comments:
~~~~~~~~~

-  MK would prefer that ``fingerprint.md5`` from the Chemistry bundle schema is folded back into the base PacBioData manifest data model.
   folded back into the manifest.

Explicit Dependencies of Components on Chemistry Bundle Resources
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  SAT

   -  CLI tools and libraries that depend on loading the chemistry.xml
      (or similar)

-  SMRT Link Analysis Services

   -  Call pipelines with proper ENV var to enable tools called within
      the pipeline to extend the tools' loaded chemistry registry
   -  Raw calls to pipelines via pbsmrtpipe should work as expected
      using the above model

-  SMRT Link Services

   -  Exposes raw bundle as a webservice
   -  Exposes a web service interface to update the bundle from a
      location (TBD)
   -  SMRT Link Service load the most recent Chemistry Version and load the
      PacBioAutomationConstraints.xml to SL UI RunDesign and Sample
      Setup
   -  Requires the default chemistry bundle to be included at build time

-  SMRT Link UI:

   -  Run Design and Sample Setup Apps required the data from
      PacBioAutomation Constraints exposed as a webservice from SMRT
      Link

-  ICS:

   -  Loads and fetches bundle from SL Services

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


SL Bundle Services
~~~~~~~~~~~~~~~~~~

SMRT Link Web Service Bundle Interface

Get a List of All SL Bundles

::

    GET /smrt-link/bundles # Returns a list of Bundle Resource instances

Get a List of SL Bundle by bundle type id (e.g., "chemistry")

::

    GET /smrt-link/bundles/{bundle-type-id} # Returns a list of Bundle instances of type 'bundle-type-id'

Bundle Resource Data Model:

-  type {String} Bundle type id (e.g., "chemistry")
-  version: {String} SemVer of the bundle. Unique Identifer to bundle
   resource within a bundle type. The bundle version comparisions will
   be performed following the semver spec.
-  importedAt: {DateTime} When the bundle was imported at
-  url: {URL} Download Link URL for file(s) (as .tgz?) servered from SL

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


Bundles Stored within the SL System install
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  TODO {DEP}: Persist location across upgrades
-  {SL}: Add configuration to smrtlink-system-conf.json to provide
   a configurable path via "smrtflow.server.bundleDir. This must be read+write by the user that launched the services.
-  TODO {SL,DEP}: This should be pulled from the Update pbbundler to build bundle with newest bundle from
   http://mkocher@bitbucket.nanofluidics.com:7990/scm/~mkocher/chemistry-bundle.git



Chemistry Bundle Specifics SL PartNumbers and Automation Constraints WebService
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The ``definitions/PacBioAutomationConstraints.xml`` is loaded from most
recent chemistry bundle. This is translated from XML (via jaxb) and
exposed as JSON as a webservice. This service will be used by the
RunDesign and SampleSetup UI application in SL.

::

    GET /smrt-link/automation-constraints # Returns a single PacBioAutomationConstraints JSON response

Note, if there is not a chemistry bundle loaded, the response will
return a 404.

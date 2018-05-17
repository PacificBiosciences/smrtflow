PacBio Update Server 
====================

A PacBio update server is a system configured to run Stand Alone Chemistry Update Bundle Server process. The Update Bundle Server is in sbt subproject *smrt-server-bundle* in smrtflow.


Requirements
~~~~~~~~~~~~

-  CentOS server, currently CentOS 7.3.1611, with Java installed.
-  Update Bundle Server package (see Packaging Update Bundle Server below)

Packaging Chemistry Update Bundle Server Build
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

For Manual build, *sbt smrt-server-bundle/{compile,pack}* will produce an exe in *smrt-server-bundle/target/pack/bin*. See detailed build instructions here:  http://bitbucket.nanofluidics.com:7990/projects/SL/repos/smrtflow/browse/docs/pacbio_bundles.rst

Build Update Bundle server, using the instructions in section "Building a Stand Alone Chemistry Update Bundle Server".

Create Update Bundle Server tarball

::

    $> tar -czf smrt-server-data-bundle-<VERSION>.tgz bin/smrt-server-data-bundle lib VERSION

Install
~~~~~~~

Copy the install tarball for the smrt-server-data-bundle server onto the system.  The following examples assume it has
been stored in /var/tmp.

Create the install directory.

::

    $> mkdir -p /opt/pacbio/smrt-server-data-bundle/<VERSION>
 
Untar the installation tarball into the install directory.

::

    $> tar -C /opt/pacbio/smrt-server-data-bundle/<VERSION> -xzf /var/tmp/smrt-server-data-bundle-<VERSION>.tgz

Create the "current" symlink referencing the newly installed version.

::

    $> ln -s <VERSION> /opt/pacbio/smrt-server-data-bundle/current

Create /opt/pacbio/smrt-server-data-bundle/smrt-server-data-bundle.service (see systemd service definition section)

Enable the systemd service by creating a symlink to the service definition file in /etc/systemd/system/multi-user.target.wants.

::

    $> ln -s /opt/pacbio/smrt-server-data-bundle/smrt-server-data-bundle.service /etc/systemd/system/multi-user.target.wants/smrt-server-data-bundle.service

Instruct systemd to reread all service definitions so that it becomes aware of rhte smrt-server-data-bundle service.

::

    $> systemctl daemon-reload

Start the smrt-server-data-bundle service.

::

    $> systemctl start smrt-server-data-bundle

Configuration
~~~~~~~~~~~~~

PB_SERVICES_PORT=8084
SMRTFLOW_BUNDLE_DIR=/opt/pacbio/smrt-server-data-bundle/chemistry-updates

The above environment variables are set as part of the systemd service that starts the smrt-server-data-bundle service.

Systemd service definition
~~~~~~~~~~~~~~~~~~~~~~~~~~

smrt-server-data-bundle.service contents::

    [Unit]
    Description=PacBio SMRTLink Update-Only server
    Documentation=
    After=network-online.target

    [Service]
    Type=simple
    WorkingDirectory=/opt/pacbio/smrt-server-data-bundle/current
    User=root
    Environment=SEGFAULT_SIGNALS=all PB_SERVICES_PORT=8084 SMRTFLOW_BUNDLE_DIR=/opt/pacbio/smrt-server-data-bundle/chemistry-updates
    StandardOutput=journal
    ExecStart=/opt/pacbio/smrtlink-server-update-only/current/bin/smrt-server-data-bundle 
    NotifyAccess=all
    ## Restart the process if it fails (which means !=0 exit, abnormal termination, or abort or watchdog)
    RestartSec=5s
    Restart=on-failure
    ## try starting up to this many times:
    StartLimitBurst=6
    ## ... within this time limit:
    StartLimitInterval=5min
    ## ... otherwise, reboot the machine.
    #StartLimitAction=reboot-force
    StartLimitAction=none

    TimeoutStopSec=10s

    [Install]
    WantedBy=multi-user.target


Upgrade 
~~~~~~~

Copy the install tarball for the smrt-server-data-bundle server onto the system.  The following examples assume it has
been stored in /var/tmp.

Create the install directory.

::
    $> mkdir -p /opt/pacbio/smrt-server-data-bundle/<VERSION>

Untar the installation tarball into the install directory.

::

    $> tar -C /opt/pacbio/smrt-server-data-bundle/<VERSION> -xzf /var/tmp/smrt-server-data-bundle-<VERSION>.tgz

Stop the smrt-server-data-bundle service.

::

    $> systemctl stop smrt-server-data-bundle

The "prev" symlink, if it exists points to the n-1 version in case an upgrade needs to be rolled back.  It needs to 
be removed.

::

    $> rm -f /opt/pacbio/smrt-server-data-bundle/prev

The "current" version now becomes the "prev" version by renaming the current symlink.

::

    $> mv /opt/pacbio/smrt-server-data-bundle/current /opt/pacbio/smrt-server-data-bundle/prev

Create the "current" symlink referencing the newly installed version.

::

    $> ln -s <VERSION> /opt/pacbio/smrt-server-data-bundle/current

Restart the smrt-server-data-bundle service

::

    $> systemctl start smrt-server-data-bundle

Automated build and deploy
~~~~~~~~~~~~~~~~~~~~~~~~~~

The steps for building, creating the release bundle, and installing the release bundle that are documented above, have 
been automated as a Bamboo job:  http://bamboo.nanofluidics.com:8085/browse/DEP-SD.  This job is triggered by changes
to the master branch in the smrtflow repository.  The build results are automatically deployed to smrtlink-update-staging
but must be manually deployed,  as part of a release process, to smrtlink-update.
#!/bin/sh

# Finds existing lims.yml files
#
# This script is intended to help pull the full list of current lims.yml files so that they can
# later be loaded to smrt-server-lims via the /import web service.
#
# It runs much faster on any login node. e.g. ssh you@login14 then run it.
#
# If running from your Mac laptop, symlink /net/pbi to /pbi so that it'll correctly translate the
# the mapped drive's file path on your laptop. Note, `/net/pbi` just auto-mounts /pbi as a NFS.
#
# `find` takes awhile. You may wish to save the output to a file by appending `> dump_lims_yml.txt`
#
# See also `import_lims_yml.sh` and `batch_import_lims_yml.sh`

find -L /pbi/collections -name "lims.yml"
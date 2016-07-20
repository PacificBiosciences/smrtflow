#!/bin/sh

# Imports a single lims.yml file via curl to smrt-server-lims
#
# This script provides the command for uploading a single lims.yml file to a smrt-server-lims
# server. It may be used in batch via `batch_import_lims_yml.sh`, which uses xargs.
#
# Run this by passing the path of the lims.yml file.
#
# $ ./import_lims_yml.sh "/pbi/collections/282/2820164/Density-110415v1_420/B01_1/lims.yml"
#
# See also `find_lims_yml.sh` and `batch_import_lims_yml.sh`

HOST="127.0.0.1"
PORT="8070"

# Buffer + echo is here because most responses don't have a newline. This pretty prints them.
OUTPUT=$(curl --form "fileupload=@$1" http://$HOST:$PORT/import)
echo $OUTPUT
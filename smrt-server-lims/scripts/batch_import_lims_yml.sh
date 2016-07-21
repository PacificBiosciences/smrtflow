#!/bin/sh

# Multi-threaded, batch import of lims.yml files
#
# This script uses `xargs` to run many (10x by default) `import_lims_yml.sh` scripts in parallel. It
# greatly speeds up bulk importing of these files, especially if accessing them via OSX's /net.
#
# You must pass a list of paths to the script, typically either a file or the direct output of
# `find_lims_yml.sh`.
#
# $ cat list_of_lims_ymls_paths.txt | ./batch_import_lims_yml.sh
#
# Alternatively and slower, pipe the `find` output.
#
# $ ./find_lims_yml.sh | ./batch_import_lims_yml.sh
#
# Be careful increasing the -P argument. Importing opens and closes many small files and you may
# hit OS enforced limits if importing thousands of files.
#
# See also `find_lims_yml.sh` and `import_lims_yml.sh`

xargs -P 10 -n 1 ./import_lims_yml.sh
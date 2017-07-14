#!/bin/bash

set -o errexit
set -o pipefail
# set -o nounset
set -o xtrace

if ! [ "${PGDATA:-}" ] || ! [ "${PGPORT:-}" ]; then
    echo "PGDATA and PGPORT must be set"
    exit 1
fi

export PB_TEST_DATA_FILES=`readlink -f repos/PacBioTestData/data/files.json`

source /mnt/software/Modules/current/init/bash
module load jdk/1.8.0_71 sbt postgresql

make jsontest

mkdir -p tmp
# postgres initialization
rm -rf $PGDATA && mkdir -p $PGDATA
initdb
perl -pi.orig -e "s/#port\s*=\s*(\d+)/port = $PGPORT/" $PGDATA/postgresql.conf
pg_ctl -w -l $PGDATA/postgresql.log start
createdb smrtlinkdb
psql -d smrtlinkdb < ${bamboo_working_directory}/extras/db-init.sql
psql -d smrtlinkdb < ${bamboo_working_directory}/extras/test-db-init.sql
export SMRTFLOW_DB_PORT=$PGPORT
export SMRTFLOW_TEST_DB_PORT=$PGPORT


env TMP=`pwd`/tmp sbt -no-colors compile test publish

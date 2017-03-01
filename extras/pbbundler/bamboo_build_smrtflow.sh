#!/bin/bash -ex

if ! [ "${PGDATA:-}" ] || ! [ "${PGPORT:-}" ]; then
    echo "PGDATA and PGPORT must be set"
    exit 1
fi

mkdir -p tmp
/opt/python-2.7.9/bin/python /mnt/software/v/virtualenv/13.0.1/virtualenv.py tmp/venv
source tmp/venv/bin/activate
cd repos/PacBioTestData
python setup.py install
cd ../..

source /mnt/software/Modules/current/init/bash
module load jdk/1.8.0_71 sbt postgresql

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


env PB_TEST_DATA_FILES="`pbdata path`" TMP=`pwd`/tmp sbt -no-colors compile test publish
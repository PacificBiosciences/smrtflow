#!/bin/bash

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

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

echo "Loading Conda GNU module"
module load anaconda

echo "Building Conda Env"

# This only really needs to be done once
conda config --add channels defaults
conda config --add channels conda-forge
conda config --add channels bioconda

# This needs to be host specific?
env_name="testenv01"

echo "Setting up new env '${env_name}'"
# how to do you initialize this without any dep?
conda create --yes -n "${env_name}" numpy cython matplotlib

source activate "${env_name}"

#conda install --yes cython
#conda install --yes numpy
#conda install --yes matplotlib
conda install --yes -c bioconda pysam=0.11.2.2

# Install all PB py dependencies
pip install -r pbcommand/REQUIREMENTS.txt
cd pbcommand && pip install . && cd -

pip install -r pbcore/requirements.txt
cd pbcore && pip install . && cd -

pip install -r pbcoretools/requirements.txt
cd pbcoretools && pip install . && cd -

cd pbsmrtpipe && pip install . && cd -
pip install -r pbsmrtpipe/REQUIREMENTS.txt

cd pbreports && pip install . && cd -
pip install -r pbreports/REQUIREMENTS.txt

# Sanity test
dataset --help
pbsmrtpipe --help
python -m pbreports.report.mapping_stats --help



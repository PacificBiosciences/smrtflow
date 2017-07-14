#!/bin/bash

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

export PB_TEST_DATA_FILES=`readlink -f repos/PacBioTestData/data/files.json`

source /mnt/software/Modules/current/init/bash
module load jdk/1.8.0_71 sbt postgresql

# Validate JSON files within the root directory. Note any dirs that are added to the root will be processed.
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

# MK. It's not clear to me why this is explicitly being set.
TDIR="$(pwd)/tmp"

# Cleanup from any previous build (if exists)
rm -rf "$TDIR"

mkdir "$TDIR"
# MK. Disabling nexus publishing. I don't believe we're using the artifacts anywhere. Add "publish" here to push to nexus.
env TMP="$TDIR" sbt -no-colors compile test

#https://github.com/conda/conda/issues/3200 This appears to be fixed in 4.4.0
set +o nounset

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

conda install --yes -c bioconda pysam=0.11.2.2
conda install --yes -c bioconda ngmlr

# Install all PB py dependencies
pip install -r repos/pbcommand/REQUIREMENTS.txt
cd repos/pbcommand && pip install . && cd -

pip install -r repos/pbcore/requirements.txt
cd repos/pbcore && pip install . && cd -

pip install -r repos/pbcoretools/requirements.txt
cd respo/pbcoretools && pip install . && cd -

cd repos/pbsmrtpipe && pip install . && cd -
pip install -r repos/pbsmrtpipe/REQUIREMENTS.txt

cd repos/pbreports && pip install . && cd -
pip install -r repos/pbreports/REQUIREMENTS.txt

# Sanity test
dataset --help
pbsmrtpipe --help
python -m pbreports.report.mapping_stats --help


make test-int
#!/bin/bash

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

PB_TEST_DATA_FILES=$(readlink -f repos/PacBioTestData/data/files.json)
export PB_TEST_DATA_FILES="${PB_TEST_DATA_FILES}"

source /mnt/software/Modules/current/init/bash
module load jdk/1.8.0_71 sbt postgresql

ROOT_REPOS="$(pwd)/repos"

# MK. It's not clear to me why this is explicitly being set.
TDIR="$(pwd)/tmp"

# Cleanup from any previous build (if exists)
rm -rf "$TDIR"

mkdir "$TDIR"

# Validate JSON files within the root directory. Note any dirs that are added to the root will be processed.
make jsontest

# postgres initialization
rm -rf "$PGDATA" && mkdir -p "$PGDATA"
initdb
perl -pi.orig -e "s/#port\s*=\s*(\d+)/port = $PGPORT/" "$PGDATA/postgresql.conf"
pg_ctl -w -l "$PGDATA/postgresql.log" start
createdb smrtlinkdb
psql -d smrtlinkdb < "${bamboo_working_directory}/extras/db-init.sql"
psql -d smrtlinkdb < "${bamboo_working_directory}/extras/test-db-init.sql"
export SMRTFLOW_DB_PORT=$PGPORT
export SMRTFLOW_TEST_DB_PORT=$PGPORT

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

set +e
conda info --envs | grep "${env_name}"
env_status=$?

set -e
if [[ "${env_status}" -eq 0 ]]; then
    echo "Env ${env_name} was already created. Using cached env"
else
    echo "Creating ${env_name}"
    conda create --quiet --yes -n "${env_name}" numpy cython matplotlib
fi

source activate "${env_name}"
which python

conda install --yes -c bioconda pysam=0.11.2.2
conda install --yes -c bioconda ngmlr

# Install all PB py dependencies
pip install -r "${ROOT_REPOS}/pbcommand/REQUIREMENTS.txt"
cd repos/pbcommand && pip install . && cd -

pip install -r "${ROOT_REPOS}/pbcore/requirements.txt"
cd "${ROOT_REPOS}/pbcore" && pip install . && cd -

pip install -r "${ROOT_REPOS}/pbcoretools/requirements.txt"
cd "${ROOT_REPOS}/pbcoretools" && pip install . && cd -

cd "${ROOT_REPOS}/pbsmrtpipe" && pip install . && cd -
pip install -r "${ROOT_REPOS}/pbsmrtpipe/REQUIREMENTS.txt"

cd "${ROOT_REPOS}/pbreports" && pip install . && cd -
pip install -r "${ROOT_REPOS}/pbreports/REQUIREMENTS.txt"

# Sanity test
which python
dataset --help
pbsmrtpipe --help
python -m pbreports.report.mapping_stats --help


make test-int
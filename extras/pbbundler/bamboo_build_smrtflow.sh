#!/bin/bash

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SMRTFLOW_ROOT_DIR=$(readlink -f "${__dir}/../..")

PB_TEST_DATA_FILES=$(readlink -f repos/PacBioTestData/data/files.json)
export PB_TEST_DATA_FILES="${PB_TEST_DATA_FILES}"
SMRT_PIPELINE_BUNDLE_DIR=$(readlink -f repos/pbpipeline-resources)
export SMRT_PIPELINE_BUNDLE_DIR="${SMRT_PIPELINE_BUNDLE_DIR}"

source /mnt/software/Modules/current/init/bash
module load jdk/1.8.0_144 sbt postgresql blasr ngmlr

ROOT_REPOS="${SMRTFLOW_ROOT_DIR}/repos"

PSQL_LOG="${SMRTFLOW_ROOT_DIR}/postgresql.log"

# We currently have a pretty poor model for cleaning up tmp files within the scala code
# set this globablly so all tests will run with this configuration
TDIR="$(pwd)/tmp"
export TMP="${TDIR}"

# Cleanup from any previous build (if exists)
rm -rf "$TDIR"

mkdir "$TDIR"

# Validate JSON files within the root directory. Note any dirs that are added to the root will be processed.
make jsontest

# cleanup from previous run if necessary
rm -rf "$PGDATA" && mkdir -p "$PGDATA"
rm -rf "${PSQL_LOG}"

# postgres initialization
initdb
perl -pi.orig -e "s/#port\s*=\s*(\d+)/port = $PGPORT/" "$PGDATA/postgresql.conf"
pg_ctl -w -l "${PSQL_LOG}" start
createdb smrtlinkdb
psql -d smrtlinkdb < "${SMRTFLOW_ROOT_DIR}/extras/db-init.sql"
psql -d smrtlinkdb < "${SMRTFLOW_ROOT_DIR}/extras/test-db-init.sql"
export SMRTFLOW_DB_PORT=$PGPORT
export SMRTFLOW_TEST_DB_PORT=$PGPORT

# MK. Disabling nexus publishing. I don't believe we're using the artifacts anywhere. Add "publish" here to push to nexus.
sbt -no-colors compile scalafmt::test test

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
    conda create --quiet --yes -n "${env_name}" numpy cython matplotlib scipy
fi

source activate "${env_name}"
which python

conda install --quiet --yes -c bioconda pysam=0.11.2.2
conda install --quiet --yes -c bioconda ngmlr

function uninstall_pkg () {
  local pkg=$1
  x=$(pip freeze | grep ${pkg}| wc -l)
  if [[ "$x" -ne "0" ]] ; then
    pip uninstall -y "${pkg}"
  fi
}

# Install all PB py dependencies
pip install -r "${ROOT_REPOS}/pbcommand/REQUIREMENTS.txt"
uninstall_pkg pbcommand
pip install "${ROOT_REPOS}/pbcommand"

pip install -r "${ROOT_REPOS}/pbcore/requirements.txt"
uninstall_pkg pbcore
pip install "${ROOT_REPOS}/pbcore"

pip install -r "${ROOT_REPOS}/pbcoretools/requirements.txt"
uninstall_pkg pbcoretools
pip install "${ROOT_REPOS}/pbcoretools"

pip install -r "${ROOT_REPOS}/pbreports/REQUIREMENTS.txt"
uninstall_pkg pbreports
pip install "${ROOT_REPOS}/pbreports"

pip install -r "${ROOT_REPOS}/pbsmrtpipe/REQUIREMENTS.txt"
uninstall_pkg pbsmrtpipe
pip install "${ROOT_REPOS}/pbsmrtpipe"

cd "${SMRTFLOW_ROOT_DIR}"

# Sanity test
which python
echo "Printing PacBio core python package versions"
python -c 'import pbcommand; print pbcommand.get_version()'
python -c 'import pbcore; print pbcore.__VERSION__'
python -c 'import pbsmrtpipe; print pbsmrtpipe.get_version()'
python -c 'import pbcoretools; print pbcoretools.__VERSION__'

dataset --help
pbsmrtpipe --help
python -m pbreports.report.mapping_stats --help

make test-int
make test

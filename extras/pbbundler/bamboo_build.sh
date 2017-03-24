#!/usr/bin/env bash

# this will be in the name of output tar.gz file
BUNDLE_VERSION="0.14.0"

echo "Bamboo build number '${bamboo_buildNumber}'"

# this script assumes that the directory containing the smrtflow repo also
# contains ui and the python repos
g_progdir=$(dirname "$0");
g_progdir_abs=$(readlink -f "$g_progdir");
SMRTFLOW_ROOT=$(readlink -f "$g_progdir"/../..);
SRC=$(readlink -f "$SMRTFLOW_ROOT"/..);
UI_ROOT="${SRC}/ui"
BUNDLE_DEST="${PBBUNDLER_DEST}"
if [ -z "$BUNDLE_DEST" ]; then
  BUNDLE_DEST="/mnt/secondary/Share/smrtserver-bundles-mainline"
  echo "Using default BUNDLE_DEST=${BUNDLE_DEST}"
fi

cd $SMRTFLOW_ROOT
SMRTFLOW_SHA="`git rev-parse --short HEAD`"
cd $UI_ROOT
UI_SHA="`git rev-parse --short HEAD`"
echo "smrtflow revision: $SMRTFLOW_SHA ; UI revision: $UI_SHA"

BUNDLER_ROOT="${SMRTFLOW_ROOT}/extras/pbbundler"
SL_IVY_CACHE=~/.ivy2-pbbundler-mainline-sl

WSO2_ZIP=/mnt/secondary/Share/smrtserver-resources/wso2am-2.0.0.zip
TOMCAT_TGZ=/mnt/secondary/Share/smrtserver-resources/apache-tomcat-8.0.26.tar.gz

set -o errexit
set -o pipefail
#set -o nounset # this makes virtualenv fail
# set -o xtrace

# Set magic variables for current file & dir
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
__root="$(cd "$(dirname "${__dir}")" && pwd)" # <-- change this
__file="${__dir}/$(basename "${BASH_SOURCE[0]}")"
__base="$(basename ${__file} .sh)"

SL_ANALYSIS_SERVER="smrt-server-link"

echo "Starting building ${BUNDLE_VERSION}"

source /mnt/software/Modules/current/init/bash

module load jdk/1.8.0_71
module load sbt
module load nodejs/4.1.2

echo "Running java version $(java -version)"
echo "Running sbt $(which sbt)"

cd $SRC
if [ -z "$PBBUNDLER_NO_VIRTUALENV" ]; then
  module load python/2.7.9
  ve=${SRC}/ve
  echo "Creating Virtualenv $ve"
  python /mnt/software/v/virtualenv/13.0.1/virtualenv.py $ve
  source $ve/bin/activate
  pip install fabric
fi

RPT_JSON_PATH="${SRC}/resolved-pipeline-templates"
if [ "$BAMBOO_USE_PBSMRTPIPE_ARTIFACTS" != "true" ]; then
  if [ -z "$PBBUNDLER_NO_VIRTUALENV" ]; then
    echo "Installing pbsmrtpipe to virtualenv"
    cd ${SRC}/pbcore
    pip install -r requirements.txt
    python setup.py install
    cd ..
    (cd ${SRC}/pbcommand && make clean && python setup.py install)
    (cd ${SRC}/pbsmrtpipe && make clean && python setup.py install)
  fi

  if [ ! -d ${RPT_JSON_PATH} ]; then
    mkdir ${RPT_JSON_PATH}
  fi

  echo "Generating resolved pipeline templates in ${RPT_JSON_PATH}"
  rm -f ${RPT_JSON_PATH}/*.json
  pbsmrtpipe show-templates --output-templates-json ${RPT_JSON_PATH}

  echo "Generating pipeline datastore view rules"
  VIEW_RULES="${SMRTFLOW_ROOT}/smrt-server-link/src/main/resources/pipeline-datastore-view-rules"
  python -m pbsmrtpipe.pb_pipelines.pb_pipeline_view_rules --output-dir $VIEW_RULES

  # FIXME this won't be run if we use build artifacts - need some other way
  # to run validation
  python -m pbsmrtpipe.testkit.validate_presets ${SMRTFLOW_ROOT}/smrt-server-link/src/main/resources/resolved-pipeline-template-presets

fi

# don't need to do any building for this
echo "Installing report view rules from pbreports"
REPORT_RULES="${SMRTFLOW_ROOT}/smrt-server-link/src/main/resources/report-view-rules"
cp ${SRC}/pbreports/pbreports/report/specs/*.json $REPORT_RULES/



cd $BUNDLER_ROOT
# Build Secondary Analysis Services + SMRT Link UI
fab build_smrtlink_services_ui:"${BUNDLE_VERSION}-${SMRTFLOW_SHA}.${UI_SHA}","${UI_ROOT}/apps/smrt-link","${SMRTFLOW_ROOT}","${RPT_JSON_PATH}",publish_to="${BUNDLE_DEST}",ivy_cache="${SL_IVY_CACHE}",analysis_server="${SL_ANALYSIS_SERVER}",wso2_api_manager_zip="${WSO2_ZIP},tomcat_tgz=${TOMCAT_TGZ}"

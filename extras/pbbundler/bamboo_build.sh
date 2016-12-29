#!/usr/bin/env bash

# this will be in the name of output tar.gz file
BUNDLE_VERSION="0.11.0"

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
if [ -z "$GIT_SHA" ]; then
  GIT_SHA="`git rev-parse --short HEAD`"
fi
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

INTERNAL_BUILD=0
SL_ANALYSIS_SERVER="smrt-server-analysis"
arg1="${1:-}"
if [ ! -z "$arg1" ] && [ "$arg1" = "--internal" ]; then
  INTERNAL_BUILD=1
  BUNDLE_VERSION="internal-${BUNDLE_VERSION}"
  SL_ANALYSIS_SERVER="smrt-server-analysis-internal"
fi

echo "Starting building ${BUNDLE_VERSION}"

source /mnt/software/Modules/current/init/bash

module load jdk/1.8.0_40
module load sbt
module load nodejs/4.1.2
module load python/2.7.9

echo "Running java version $(java -version)"
echo "Running sbt $(which sbt)"

cd $SRC
ve=${SRC}/ve
echo "Creating Virtualenv $ve"

/opt/python-2.7.9/bin/python /mnt/software/v/virtualenv/13.0.1/virtualenv.py $ve
source $ve/bin/activate

# FIXME too much overhead here - we have to install many bulky dependencies to
# use these modules
echo "Installing pbsmrtpipe to virtualenv"
cd ${SRC}/pbcore
pip install -r requirements.txt
python setup.py install
cd ..
(cd ${SRC}/pbcommand && make clean && python setup.py install)
(cd ${SRC}/pbsmrtpipe && make clean && python setup.py install)

pip install fabric

cd $BUNDLER_ROOT

rpt_json_path="${SRC}/resolved-pipeline-templates"

if [ ! -d ${rpt_json_path} ]; then
  mkdir ${rpt_json_path}
fi

echo "Generating resolved pipeline templates in ${rpt_json_path}"
rm -f ${rpt_json_path}/*.json
pbsmrtpipe show-templates --output-templates-json ${rpt_json_path}

echo "Installing report view rules from pbreports"
REPORT_RULES="${SMRTFLOW_ROOT}/smrt-server-analysis/src/main/resources/report-view-rules"
cp ${SRC}/pbreports/pbreports/report/specs/*.json $REPORT_RULES/

echo "Generating pipeline datastore view rules"
VIEW_RULES="${SMRTFLOW_ROOT}/smrt-server-analysis/src/main/resources/pipeline-datastore-view-rules"
python -m pbsmrtpipe.pb_pipelines.pb_pipeline_view_rules --output-dir $VIEW_RULES

# giant hack to allow us to display internal pipelines
if [ $INTERNAL_BUILD -eq 1 ]; then
  echo "Making adjustments for internal build..."
  CONFIG_FILE=`find ${UI_ROOT} -name "app-config.json"`
  if [ -z "$CONFIG_FILE" ]; then
    echo "Can't find app-config.json"
    exit 1
  fi
  sed -i 's/"isInternalModeEnabled": false/"isInternalModeEnabled": true/;' $CONFIG_FILE
fi

python -m pbsmrtpipe.testkit.validate_presets ${SMRTFLOW_ROOT}/smrt-server-analysis/src/main/resources/resolved-pipeline-template-presets

# write a simple text file of workflow options that the smrtlink installer can
# use to validate command-line arguments to get the XML or JSON of the workflow level options
# FIXME. This needs to be deleted. Using pbsmrtpipe show-workflow-options -j default-pbsmrtpipe workflow-options.json -o default-preset.xml
# Copy this into the bundle template before building. See https://bugzilla.nanofluidics.com/show_bug.cgi?id=32427
OPTS_PATH="${BUNDLER_ROOT}/smrtlink_services_ui/workflow_options.txt"
pbsmrtpipe show-workflow-options | grep "^Option" | sed 's/.*:\ *//; s/.*\.//;' > $OPTS_PATH

# Build Secondary Analysis Services + SMRT Link UI
fab build_smrtlink_services_ui:"${BUNDLE_VERSION}-${GIT_SHA}","${UI_ROOT}/apps/smrt-link","${SMRTFLOW_ROOT}","${rpt_json_path}",publish_to="${BUNDLE_DEST}",ivy_cache="${SL_IVY_CACHE}",analysis_server="${SL_ANALYSIS_SERVER}",wso2_api_manager_zip="${WSO2_ZIP},tomcat_tgz=${TOMCAT_TGZ}"

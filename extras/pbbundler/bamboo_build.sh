#!/usr/bin/env bash

set -o errexit
set -o pipefail
#set -o nounset # this makes virtualenv fail
# set -o xtrace

# this will be in the name of output tar.gz file
# (FIXME)(6-5-2017)(mkocher) Unclear blast radius. This should be migrated to only use the bamboo global build number
BUNDLE_VERSION="0.16.${bamboo_globalBuildNumber}"

echo "Bamboo local build number '${bamboo_buildNumber}' Global build number '${bamboo_globalBuildNumber}'"

# this script assumes that the directory containing the smrtflow repo also
# contains ui and the python repos
g_progdir=$(dirname "$0");
g_progdir_abs=$(readlink -f "$g_progdir");
SMRTFLOW_ROOT=$(readlink -f "$g_progdir"/../..);
SRC=$(readlink -f "$SMRTFLOW_ROOT"/..);
UI_ROOT="${SRC}/ui"
BUNDLE_DEST="${PBBUNDLER_DEST}"

DOC_HELP_ROOT="${SRC}/sl-helps"
DOC_XSD_ROOT="${SRC}/xsd-datamodels"

DOC_ROOT=$(mktemp -d)/docs
mkdir -p "${DOC_ROOT}"

# Input Validation
if [ -z "$BUNDLE_DEST" ]; then
  BUNDLE_DEST="/mnt/secondary/Share/smrtserver-bundles-mainline"
  echo "Using default BUNDLE_DEST=${BUNDLE_DEST}"
fi

CHEM_PB_VERSION=$(cat $g_progdir/chemistry-pb_version)

cd $SMRTFLOW_ROOT
SMRTFLOW_SHA="`git rev-parse --short HEAD`"
cd $UI_ROOT
UI_SHA="`git rev-parse --short HEAD`"
echo "smrtflow revision: $SMRTFLOW_SHA ; UI revision: $UI_SHA"

BUNDLER_ROOT="${SMRTFLOW_ROOT}/extras/pbbundler"
if [ -z "$SL_IVY_CACHE" ] ; then
    SL_IVY_CACHE=~/.ivy2-pbbundler-mainline-sl
fi

# set up ICS credentials
if [ -z "$PB_ICS_USER" ] || [ -z "$PB_ICS_PASSWORD" ]; then
  echo "ERROR: PB_ICS_USER and PB_ICS_PASSWORD must be defined first"
  exit 1
fi
echo "{\"wso2User\": \"$PB_ICS_USER\", \"wso2Password\": \"$PB_ICS_PASSWORD\"}" > $BUNDLER_ROOT/smrtlink_services_ui/ics-default-credentials.json
chmod 400 $BUNDLER_ROOT/smrtlink_services_ui/ics-default-credentials.json

WSO2_ZIP=/mnt/secondary/Share/smrtserver-resources/wso2am-2.0.0-stock-plus-postgres.zip
if [ -z "$TOMCAT_TGZ" ] ; then
     TOMCAT_TGZ=/pbi/dept/secondary/builds/develop/current_thirdpartyall-release_installdir/java/tomcat-pbtarball/tomcat-pbtarball_8.5.31/tarball/tomcat-pbtarball_8.5.31.tar.gz
fi

echo "Starting building ${BUNDLE_VERSION}"

source /mnt/software/Modules/current/init/bash

module load jdk/1.8.0_144
module load sbt

# Load the correct Node version for this build
source "${UI_ROOT}/apps/smrt-link/build/include_modules.sh"

echo "Running java version $(java -version)"
echo "Running sbt $(which sbt)"

cd $SRC
if [ -z "$PBBUNDLER_NO_VIRTUALENV" ]; then
  module load python/2.7.9
  ve=${SRC}/ve
  echo "Creating Virtualenv $ve"
  python /mnt/software/v/virtualenv/13.0.1/virtualenv.py $ve
  source $ve/bin/activate
  pip install fabric==1.14.0
  pip install sphinx
fi

# A prebuilt copy of the swagger-ui
SWAGGER_UI_DIR=/mnt/secondary/Share/smrtserver-resources/swagger-ui
SWAGGER_UI_OUTPUT_DIR="${DOC_ROOT}/services"

mkdir -p "${SWAGGER_UI_OUTPUT_DIR}"
cp ${SWAGGER_UI_DIR}/* "${SWAGGER_UI_OUTPUT_DIR}"
cp "${SMRTFLOW_ROOT}/smrt-server-link/src/main/resources/smrtlink_swagger.json" "${SWAGGER_UI_OUTPUT_DIR}"

# Copy docs from sl-help into ${DOC_ROOT}/help
if [[ -d "${DOC_HELP_ROOT}" ]]; then
  cp -R "${DOC_HELP_ROOT}" "${DOC_ROOT}/help"
  cp -R "${DOC_XSD_ROOT}" "${DOC_ROOT}/xsd-datamodels"
fi

cd $BUNDLER_ROOT
# Build Secondary Analysis Services + SMRT Link UI
fab build_smrtlink_services_ui:"${BUNDLE_VERSION}-${SMRTFLOW_SHA}.${UI_SHA}","${UI_ROOT}/apps/smrt-link","${SMRTFLOW_ROOT}",publish_to="${BUNDLE_DEST}",ivy_cache="${SL_IVY_CACHE}",wso2_api_manager_zip="${WSO2_ZIP}",tomcat_tgz="${TOMCAT_TGZ}",chemistry_pb_version="${CHEM_PB_VERSION}",doc_dir="${DOC_ROOT}"

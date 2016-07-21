#!/bin/bash
# this only loads the smrt-analysis and smrt-server-analysis CLI tools
export PROJ_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PATH=${PROJ_DIR}/smrt-analysis/target/pack/bin:${PROJ_DIR}/smrt-server-analysis/target/pack/bin:${PROJ_DIR}/smrt-server-analysis-internal/target/pack/bin:$PATH

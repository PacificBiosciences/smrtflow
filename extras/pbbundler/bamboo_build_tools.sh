#!/bin/bash -ex

source /mnt/software/Modules/current/init/bash
module load jdk/1.8.0_71
module load sbt

SHA=`git rev-parse --short HEAD`
echo SHA is ${SHA}
rm -f pbscala*.tar.gz
sbt -no-colors smrt-analysis/pack smrt-server-base/pack smrt-server-link/pack
cp -r smrt-server-base/target/pack/* smrt-analysis/target/pack/
cp -r smrt-server-link/target/pack/* smrt-analysis/target/pack/
rm -rf smrt-analysis/target/pack/bin/*.bat

TARBALL=${bamboo_working_directory}/pbscala-packed-${SHA}.tar.gz

tar cvfz $TARBALL smrt-analysis/target/pack \
  && cp $TARBALL /mnt/secondary/Share/pbscala-tools/

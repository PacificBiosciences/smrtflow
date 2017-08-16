#!/bin/bash -ex

source /mnt/software/Modules/current/init/bash
module load jdk/1.8.0_71
module load sbt

SHA=`git rev-parse --short HEAD`
echo SHA is ${SHA}
rm -f pbscala*.tar.gz
sbt -no-colors smrt-server-link/pack

TARBALL=${bamboo_working_directory}/pbscala-packed-${SHA}.tar.gz

tar cvfz $TARBALL smrt-server-link/target/pack \
  && cp $TARBALL /mnt/secondary/Share/pbscala-tools/

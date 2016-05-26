#!/bin/bash -xe
#
# Script to update 'pbscala-packed.tar.gz' used in smrttools build.  We store
# the tarball in perforce, not git.

sbt smrt-analysis/clean smrt-analysis/compile smrt-analysis/pack
# TODO maybe later
sbt smrt-server-analysis/clean smrt-server-analysis/compile smrt-server-analysis/pack

cp -r smrt-server-analysis/target/pack/* smrt-analysis/target/pack/

cd smrt-analysis
# Remove Windows .bat files
rm -rf target/pack/bin/*.bat

tar cvfz ../pbscala-packed.tar.gz target/pack

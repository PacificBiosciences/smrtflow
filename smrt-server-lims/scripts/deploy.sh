#!/bin/bash
set -e

# set the location of needed binaries
export PATH="/mnt/software/s/sbt/0.13.8/bin:/mnt/software/j/jdk/1.8.0_20/bin:/mnt/software/g/git/2.8.3/ubuntu-1404/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

# create repo if needed
WORK_DIR="/home/jfalkner/smrt-lims"
if [ ! -d "$WORK_DIR" ]; then
   mkdir $WORK_DIR
   cd $WORK_DIR
   git clone https://github.com/PacificBiosciences/smrtflow.git
fi

# switch to the branch with the latest smrt-lims code
cd $WORK_DIR/smrtflow
git checkout smrt-server-lims
git pull

# assemble the JAR for
sbt smrt-server-lims/assembly
java -Dconfig.file=$WORK_DIR/production.conf -Xmx2g -jar smrt-server-lims/target/scala-2.11/smrt-server-lims*.jar --debug
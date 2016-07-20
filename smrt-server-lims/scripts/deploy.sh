#!/bin/bash
set -e

# load the needed binaries
source /mnt/software/Modules/current/init/bash
module load jdk/1.8.0_71
module load sbt
module load git

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
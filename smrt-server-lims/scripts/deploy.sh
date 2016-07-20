#!/bin/bash
set -e

# set the location of needed binaries
export PATH="/mnt/software/s/sbt/0.13.8/bin:/mnt/software/j/jdk/1.8.0_20/bin:/mnt/software/g/git/2.8.3/ubuntu-1404/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

# create repo if needed
GIT_DIR="/home/jfalkner/smrt-lims"
if [ ! -d "$GIT_DIR" ]; then
   mkdir $GIT_DIR
   cd $GIT_DIR
   git clone https://github.com/PacificBiosciences/smrtflow.git
fi

# switch to the branch with the latest smrt-lims code
cd $GIT_DIR/smrtflow
git checkout smrt-server-lims
git pull

# entry point for the codebase
sbt smrt-server-lims/run
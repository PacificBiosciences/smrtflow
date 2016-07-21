#!/bin/sh
set -e
# Finds and imports lims.yml files that have chagned in the last 24 hours
#
# This is part of the smrt-lims server setup. Paired with crontab/cron, it'll auto-import the latest
# lims.yml changes.
#
# Run this hourly during the work day with this crontab
# 00 09-18 * * 1-5 /home/jfalkner/smrt-lims/hourly_import/find_and_load_recent_lims_yml.sh

# setup PATH and switch to working directory for the hourly updates
WORK_DIR=/home/jfalkner/smrt-lims
export PATH=$WORK_DIR/smrtflow/smrt-server-lims/scripts/:$PATH
cd $WORK_DIR//hourly_import

# find all `lims.yml` files that changed in the last 24 hours
find -L /pbi/collections -mtime 0 -name "lims.yml" > recent_lims_yml.txt
# batch import the list via xargs + curl
batch_import_lims_yml.sh < recent_lims_yml.txt > recent_lims_yml_import.txt

# pretty format an e-mail for sendmail
echo "Subject: smrt-lims found $(wc -l recent_lims_yml_import.txt) recent lims.yml files" > email.txt
echo "" >> email.txt
echo "Auto-import recent lims.yml task. See docs at https://github.com/PacificBiosciences/smrtflow/blob/master/smrt-server-lims/README.md" >> email.txt
echo "" >> email.txt
echo "##Changed in the past 24 hours" >> email.txt
cat recent_lims_yml.txt >> email.txt
echo "" >> email.txt
echo "##smrt-lims /import status" >> email.txt
cat recent_lims_yml_import.txt >> email.txt

# e-mail the maintainers
cat email.txt | sendmail jfalkner@pacificbiosciences.com
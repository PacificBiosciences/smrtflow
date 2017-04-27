# Change Log

This file tracks changes to the public interface. 

The pbbundler version is BUNDLE_VERSION in bamboo_build.sh

## SL System Version 5.0.0

## BUNDLE_VERSION 0.15.1

- add pacBioSystem.smrtLinkSystemRoot to smrtlink-system-config.json

### BUNDLE_VERSION 0.15.0

- rename migration config to migration-config.json
- add wso2 upgrade support
- add PREVIOUS_INSTALL_DIR to migration-config.json


#### BUNDLE_VERSION 0.14.0

- Port of SL **bin/dbctl** and apply_settings_database from installerprompt to python. 
- Add support for SL legacy 4.0.0 sqlite to Postgres importing (config.json (only PB_DB_URI) in root bundle dir).
- Added **bin/upgrade** to handle legacy 4.0.0 sqlite to Postgres conversion and future Postgres schema migrations. Results of the legacy migration are accessible in [BUNDLE_ROOT]/legacy-migration.json

#### BUNDLE_VERSION 0.13.0

- Migrated from config.json to smrtlink-sytem-config.json
- Migrated from sqlite to Postgres 9.6.1
- bin/get-status supports all subcomponents with **-i** (ported to scala)
- bin/apply-config migrated to scala to remove duplication
- Introduced CHANGELOG file for SL System 4.1.0

## SL System Version 1.0.0

- Used in SL System 3.0.x - 4.0.0
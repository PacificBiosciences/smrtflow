# ChangeLog

## 0.17.0

- Added "tech-support-job-harvester" job type and companion service to SL

## 0.16.0

- smrt-server-eve. Changed the output file resolving format for Event JSON files from `{EVE-ROOT}/{SL-SYSTEM-ID}/` to `{EVE-ROOT}/{SL-SYSTEM-ID}/{YYYY}/{MM}/{DD}/`

### 0.15.0

- Services are no longer launched on 0.0.0.0. Externally accessing SMRT Link System will now require Auth prefixed routes with 'SMRTLink/1.0.0' will be required.

## 0.7.0

- Added Mailing Support when jobs have completed
- Remove support for sqlite migration (SL System 4.0.0 to SL System 5.0.0)
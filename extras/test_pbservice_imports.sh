#!/bin/bash -xe

HOST=$1
if [ -z "$HOST" ]; then
  echo "hostname required"
  exit 1
fi
PBSERVICE=smrt-server-analysis/target/pack/bin/pbservice

$PBSERVICE create-project --host $HOST --port 8070 import-test "Test imports"
$PBSERVICE import-dataset --host $HOST --port 8070 --project import-test `pbdata get subreads-xml`
$PBSERVICE import-fasta --host $HOST --port 8070 --project import-test `pbdata get lambda-fasta`
$PBSERVICE import-barcodes --host $HOST --port 8070 --project import-test `pbdata get barcode-fasta` bc_test
$PBSERVICE import-rs-movie --host $HOST --port 8070 --project import-test `pbdata get rs-movie-metadata`

.PHONY: clean jsonclean dataclean

SHELL=/bin/bash
STRESS_RUNS=1
STRESS_NAME=run
ROOT_DIR:=$(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))

clean:
	rm -f secondary-smrt-server*.log 
	rm -rf smrt-server-link/{db,jobs-root}
	sbt clean

jsonclean:
	find smrt-server-link/src/main/resources/resolved-pipeline-templates -name "*.json" | grep -v "dev_diagnostic" | xargs rm -f
	find smrt-server-link/src/main/resources/pipeline-datastore-view-rules -name "*.json" | grep -v "dev_01" | xargs rm -f
	find smrt-server-link/src/main/resources/report-view-rules -name "*.json" | grep -v "ccs_processing" | grep -v "simple_dataset" | xargs rm -f

dataclean:
	rm -rf test-data

build: 
	sbt compile

tools:
	sbt clean pack

tools-smrt-server-sim:
	sbt smrt-server-sim/{compile,pack}

xsd-java:
	rm -rf smrt-common-models/src/main/java/com/pacificbiosciences
	xjc smrt-common-models/src/main/resources/pb-common-xsds/ -d smrt-common-models/src/main/java

tools-smrt-analysis:
	sbt smrt-analysis/{compile,pack}

tools-smrt-server-link:
	sbt -no-colors smrt-server-link/{compile,pack,assembly}

tools-tarball:
	$(eval SHA := "`git rev-parse --short HEAD`")
	@echo SHA is ${SHA}
	rm -f pbscala*.tar.gz
	sbt clean smrt-analysis/pack smrt-server-base/pack smrt-server-link/pack
	cp -r smrt-server-base/target/pack/* smrt-analysis/target/pack/
	cp -r smrt-server-link/target/pack/* smrt-analysis/target/pack/
	rm -rf smrt-analysis/target/pack/bin/*.bat
	cd smrt-analysis && tar cvfz ../pbscala-packed-${SHA}.tar.gz target/pack

generate-test-pipeline-json:
	pbsmrtpipe show-templates --output-templates-json smrt-server-link/src/main/resources/resolved-pipeline-templates

repl:
	sbt smrtflow/test:console

get-pbdata: repos/PacBioTestData

repos/chemistry-data-bundle:
	mkdir -p repos
	cd repos && git clone --depth 1 http://$$USER@bitbucket.nanofluidics.com:7990/scm/sl/chemistry-data-bundle.git

repos/pacbiotestdata:
	mkdir -p repos
	cd repos && git clone --depth 1 http://$$USER@bitbucket.nanofluidics.com:7990/scm/sat/pacbiotestdata.git


import-pbdata: insert-pbdata

insert-pbdata:
	pbservice import-dataset repos/pacbiotestdata --debug

insert-mock-data:
	sbt "smrt-server-link/run-main com.pacbio.secondary.smrtlink.tools.InsertMockData"

insert-mock-data-summary: tools-smrt-server-link
	./smrt-server-link/target/pack/bin/smrt-db-tool

start-smrt-server-link:
	sbt "smrt-server-link/run"

start-smrt-server-link-jar:
	sbt "smrt-server-link/{compile,pack}"
	./smrt-server-link/target/pack/bin/smrt-server-link-analysis

test: validate-pacbio-manifests
	sbt -batch "test-only -- junitxml html console"

test-int-clean: db-reset-prod
	rm -rf jobs-root


test-int: export SMRTFLOW_EVENT_URL := https://smrtlink-eve-staging.pacbcloud.com:8083
test-int: export PACBIO_SYSTEM_REMOTE_BUNDLE_URL := http://smrtlink-update-staging.pacbcloud.com:8084
test-int: export PATH := ${ROOT_DIR}/smrt-server-link/target/pack/bin:${ROOT_DIR}/smrt-server-sim/target/pack/bin:${PATH}
test-int: export PB_TEST_DATA_FILES := ${ROOT_DIR}/repos/pacbiotestdata/data/files.json
test-int: export PB_SERVICES_MANIFEST_FILE := ${ROOT_DIR}/extras/int-test-smrtlink-system-pacbio-manifest.json

test-int: repos/pacbiotestdata repos/chemistry-data-bundle tools-smrt-server-link tools-smrt-server-sim
	@echo "PATH"
	@echo $$PATH
	@echo "TEST DATA"
	@echo $$PB_TEST_DATA_FILES
	rm -rf jobs-root
	sbt -batch -no-colors "smrt-server-sim/it:test"

jsontest:
	$(eval JSON := `find . -type f -name '*.json' -not -path '*/\.*' | grep -v './repos/' | grep -v './jobs-root/' | grep -v './tmp/' | grep -v 'target/scala'`)
	@for j in $(JSON); do \
		echo $$j ;\
		python -m json.tool $$j >/dev/null || exit 1 ;\
	done

validate-pacbio-manifests:
	$(eval JSON := `find . -name 'pacbio-manifest.json'`)
	@for j in $(JSON); do \
		echo $$j ;\
		python -m json.tool $$j >/dev/null || exit 1 ;\
	done

test-data/smrtserver-testdata:
	mkdir -p test-data
	rsync --progress -az --delete login14-biofx01:/mnt/secondary/Share/smrtserver-testdata test-data/

test-int-run-analysis:
	pbservice run-analysis --debug --port=8070 --block ./smrt-server-link/src/test/resources/analysis-dev-diagnostic-01.json

test-int-run-analysis-stress:
	pbservice run-analysis --debug --port=8070 --block ./smrt-server-link/src/test/resources/analysis-dev-diagnostic-stress-01.json

test-int-run-analysis-trigger-failure:
	pbservice run-analysis --debug --port=8070 --block ./smrt-server-link/src/test/resources/analysis-dev-diagnostic-stress-trigger-fail-01.json 

validate-report-view-rules:
	find ./smrt-server-link/src/main/resources/report-view-rules -name "*.json" -print0 | xargs -0L1 python -m json.tool

validate-pipeline-view-rules:
	find ./smrt-server-link/src/main/resources/pipeline-template-view-rules -name "*.json" -print0 | xargs -0L1 python -m json.tool

validate-resources: validate-report-view-rules validate-pipeline-view-rules

# e.g., make full-stress-run STRESS_RUNS=2
full-stress-run: test-data/smrtserver-testdata
	sbt smrt-server-link/pack
	@for i in `seq 1 $(STRESS_RUNS)`; do \
	    RUN=$(STRESS_NAME)-$$(date +%F-%T) && \
	    RUNDIR=test-output/stress-runs && \
	    OUTDIR=$$RUNDIR/$$RUN && \
	    mkdir -p $$OUTDIR && \
	    rm -f $$RUNDIR/latest && \
	    ln -s $$RUN $$RUNDIR/latest && \
	    psql < extras/db-drop.sql && \
	    psql < extras/db-init.sql && \
	    rm -rf jobs_root/* && \
	    SERVERPID=$$(bash -i -c "export PB_ENGINE_JOB_ROOT=$$OUTDIR/jobs_root; smrt-server-link/target/pack/bin/smrt-server-link-analysis --log-file $(CURDIR)/$$OUTDIR/secondary-smrt-server.log > $$OUTDIR/smrt-server-link.out 2> $$OUTDIR/smrt-server-link.err & echo \$$!") && \
	    sleep 30 && \
	    ./stress.py -x 30 --nprocesses 20 --profile $$OUTDIR/profile.json > $$OUTDIR/stress.out 2> $$OUTDIR/stress.err ; \
	    sleep 2 ; \
	    pkill -g $$SERVERPID ; \
	    sleep 2 ; \
	    psql -tAF$$'\t' smrtlink -c "select * from engine_jobs where state != 'SUCCESSFUL'" > $$OUTDIR/unsuccessful-jobs ; \
	    psql -tAF$$'\t' smrtlink -c "select je.* from job_events je inner join engine_jobs ej on je.job_id=ej.job_id where ej.state != 'SUCCESSFUL'" > $$OUTDIR/unsuccessful-job-events ; \
	done

db-reset-prod:
	psql -f ./extras/db-drop.sql
	psql -f ./extras/db-init.sql

db-reset-test:
	psql -f ./extras/test-db-drop.sql
	psql -f ./extras/test-db-init.sql

validate-swagger-smrtlink:
	swagger validate ./smrt-server-link/src/main/resources/smrtlink_swagger.json

validate-swagger-eve:
	swagger validate ./smrt-server-link/src/main/resources/eventserver_swagger.json

validate-swagger: validate-swagger-smrtlink validate-swagger-eve

check-shell:
	shellcheck -e SC1091 extras/pbbundler/bamboo_build_smrtflow.sh

SHELL=/bin/bash
STRESS_RUNS=1
STRESS_NAME=run

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

tools-sim:
	sbt smrt-server-sim/{compile,pack}

xsd-java:
	rm -rf smrt-common-models/src/main/java/com/pacificbiosciences
	xjc smrt-common-models/src/main/resources/pb-common-xsds/ -d smrt-common-models/src/main/java

tools-smrt-analysis:
	sbt smrt-analysis/{compile,pack}

tools-smrt-server-link:
	sbt smrt-server-link/{compile,pack}

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

get-pbdata: PacBioTestData

PacBioTestData:
	git clone https://github.com/PacificBiosciences/PacBioTestData.git

import-pbdata: insert-pbdata

insert-pbdata:
	pbservice import-dataset PacBioTestData --debug

insert-mock-data:
	sbt "smrt-server-link/run-main com.pacbio.secondary.smrtlink.tools.InsertMockData"

insert-mock-data-summary: tools-smrt-server-link
	./smrt-server-link/target/pack/bin/smrt-db-tool

start-smrt-server-link:
	sbt "smrt-server-link/run"

start-smrt-server-link-jar:
	sbt "smrt-server-link/{compile,pack}"
	./smrt-server-link/target/pack/bin/smrt-server-link-analysis

test:
	sbt -batch "test-only -- junitxml html console"

test-int-install-pytools:
	@echo "This should be done in a virtualenv!"
	@echo "assuming virtualenvwrapper is installed"
	virtualenv ./ve
	. ./ve/bin/activate
	pip install -r INT_REQUIREMENTS.txt
	@echo "successfully installed integration testing tools"

jsontest:
	$(eval JSON := `find . -name '*.json' -not -path '*/\.*' | grep -v 'target/scala'`)
	@for j in $(JSON); do \
		echo $$j ;\
		python -m json.tool $$j >/dev/null || exit 1 ;\
	done

test-data/smrtserver-testdata:
	mkdir -p test-data
	rsync --progress -az --delete login14-biofx01:/mnt/secondary/Share/smrtserver-testdata test-data/

test-int-import-references:
	pbservice import-dataset --debug --port=8070 test-data/smrtserver-testdata/ds-references/

test-int-import-subreads:
	pbservice import-dataset --debug --port=8070 test-data/smrtserver-testdata/ds-subreads/

test-int-import-data: test-int-import-references test-int-import-subreads

test-int-run-analysis:
	pbservice run-analysis --debug --port=8070 --block ./smrt-server-link/src/test/resources/analysis-dev-diagnostic-01.json

test-int-run-analysis-stress:
	pbservice run-analysis --debug --port=8070 --block ./smrt-server-link/src/test/resources/analysis-dev-diagnostic-stress-01.json

test-int-run-analysis-trigger-failure:
	pbservice run-analysis --debug --port=8070 --block ./smrt-server-link/src/test/resources/analysis-dev-diagnostic-stress-trigger-fail-01.json 

test-int-get-status:
	pbservice status --debug --port=8070 

test-int-run-sanity: test-int-get-status test-int-import-data test-int-run-analysis

test-sim:
	sbt "smrt-server-link/assembly"
	sbt "smrt-server-sim/pack"
	python extras/run_sim_local.py DataSetScenario

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

validate-swagger-smrtlink:
	swagger validate ./smrt-server-link/src/main/resources/smrtlink_swagger.json

validate-swagger-eve:
	swagger validate ./smrt-server-link/src/main/resources/eventserver_swagger.json

validate-swagger: validate-swagger-smrtlink validate-swagger-eve

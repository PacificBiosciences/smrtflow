SHELL=/bin/bash
STRESS_RUNS=1
SOURCE_DB=test-data/smrtserver-testdata/database/latest

clean:
	rm -f secondary-smrt-server*.log 
	rm -rf smrt-server-analysis/{db,jobs-root}
	rm -rf smrt-server-link/{db,jobs-root}
	rm -rf smrt-server-analysis-internal/{db,jobs-root}
	sbt clean


dataclean:
	rm -rf test-data

build: 
	sbt compile

tools:
	sbt clean pack


tools-smrt-analysis:
	sbt smrt-analysis/{compile,pack}

tools-smrt-analysis-internal:
	sbt smrt-server-analysis-internal/{compile,pack}

tools-smrt-server-analysis:
	sbt smrt-server-analysis/pack

repl:
	sbt smrtflow/test:console

get-pbdata: PacBioTestData

PacBioTestData:
	git clone https://github.com/PacificBiosciences/PacBioTestData.git

import-pbdata: insert-pbdata

insert-pbdata:
	pbservice import-dataset PacBioTestData --debug

insert-mock-data:
	sbt "smrt-server-analysis/run-main com.pacbio.secondary.smrtlink.tools.InsertMockData"

insert-mock-data-summary: tools-smrt-server-analysis
	./smrt-server-analysis/target/pack/bin/smrt-db-tool

start-smrt-server-analysis:
	sbt "smrt-server-analysis/run"

start-smrt-server-analysis-jar:
	sbt "smrt-server-analysis/assembly"
	java -jar smrt-server-analysis/target/scala-2.11/smrt-server-analysis-assembly-*-SNAPSHOT.jar

test:
	sbt -batch "test-only -- junitxml html console"

test-int-install-pytools:
	@echo "This should be done in a virtualenv!"
	@echo "assuming virtualenvwrapper is installed"
	virtualenv ./ve
	. ./ve/bin/activate
	pip install -r INT_REQUIREMENTS.txt
	@echo "successfully installed integration testing tools"

test-data/smrtserver-testdata:
	mkdir -p test-data
	rsync --progress -az --delete login14-biofx01:/mnt/secondary/Share/smrtserver-testdata test-data/

test-int-import-references:
	pbservice import-dataset --debug --port=8070 test-data/smrtserver-testdata/ds-references/

test-int-import-subreads:
	pbservice import-dataset --debug --port=8070 test-data/smrtserver-testdata/ds-subreads/

test-int-import-data: test-int-import-references test-int-import-subreads

test-int-run-analysis:
	pbservice run-analysis --debug --port=8070 --block ./smrt-server-analysis/src/test/resources/analysis-dev-diagnostic-01.json

test-int-run-analysis-stress:
	pbservice run-analysis --debug --port=8070 --block ./smrt-server-analysis/src/test/resources/analysis-dev-diagnostic-stress-01.json

test-int-get-status:
	pbservice status --debug --port=8070 

test-int-run-sanity: test-int-get-status test-int-import-data test-int-run-analysis

test-sim:
	sbt "smrt-server-analysis/assembly"
	sbt "smrt-server-sim/pack"
	python extras/run_sim_local.py DataSetScenario

validate-report-view-rules:
	find ./smrt-server-analysis/src/main/resources/report-view-rules -name "*.json" -print0 | xargs -0L1 python -m json.tool

validate-pipeline-view-rules:
	find ./smrt-server-analysis/src/main/resources/pipeline-template-view-rules -name "*.json" -print0 | xargs -0L1 python -m json.tool

validate-resources: validate-report-view-rules validate-pipeline-view-rules

# e.g., make full-stress-run STRESS_RUNS=2 SOURCE_DB=~/analysis_services_beta_3.1.1.db
full-stress-run: test-data/smrtserver-testdata $(SOURCE_DB)
	@for i in `seq 1 $(STRESS_RUNS)`; do \
	    RUN=run-$$(date +%s) && \
	    RUNDIR=test-output/stress-runs && \
	    OUTDIR=$$RUNDIR/$$RUN && \
	    mkdir -p $$OUTDIR && \
	    rm -f $$RUNDIR/latest && \
	    ln -s $$RUN $$RUNDIR/latest && \
	    sbt smrt-server-analysis/compile && \
	    SERVERPID=$$(bash -i -c "sbt -no-colors \"smrt-server-analysis/run --log-file $(CURDIR)/$$OUTDIR/secondary-smrt-server.log\" > $$OUTDIR/smrt-server-analysis.out 2> $$OUTDIR/smrt-server-analysis.err & echo \$$!") && \
	    sleep 30 && \
	    ./stress.py --profile $$OUTDIR/profile.json > $$OUTDIR/stress.out 2> $$OUTDIR/stress.err ; \
	    sleep 2 ; \
	    pkill -g $$SERVERPID ; \
	    sleep 2 ; \
        done

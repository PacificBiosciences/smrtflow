SHELL=/bin/bash
STRESS_RUNS=1

clean:
	rm -f secondary-smrt-server*.log
	rm -rf smrt-server-analysis/{db,jobs-root}
	sbt clean

dataclean:
	rm -rf test-data

build: 
	sbt compile

smrt-server-analysis-tools:
	sbt smrt-server-analysis/pack

insert-mock-data:
	sbt "smrt-server-link/run-main com.pacbio.secondary.smrtlink.tools.InsertMockData"

insert-mock-data-summary: smrt-server-analysis-tools
	./smrt-server-analysis/target/pack/bin/smrt-db-tool --db-uri smrt-server-link/db/analysis_services.db --quiet

start-smrt-server-analysis:
	sbt "smrt-server-analysis/run"

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
	mkdir -p $@
	scp -r login14-biofx01:/mnt/secondary/Share/smrtserver-testdata/ test-data

test-int-import-references:
	pbservice import-dataset --debug --port=8070 test-data/smrtserver-testdata/ds-references/

test-int-import-subreads:
	pbservice import-dataset --debug --port=8070 test-data/smrtserver-testdata/ds-subreads/

test-int-import-data: test-int-import-references test-int-import-subreads

test-int-run-analysis:
	pbservice run-analysis --debug --port=8070 --block ./smrt-server-analysis/src/test/resources/analysis-dev-diagnostic-01.json

test-int-get-status:
	pbservice status --debug --port=8070 

test-int-run-sanity: test-int-get-status test-int-import-data test-int-run-analysis

validate-report-view-rules:
	find ./smrt-server-analysis/src/main/resources/report-view-rules -name "*.json" -print0 | xargs -0L1 python -m json.tool

validate-pipeline-view-rules:
	find ./smrt-server-analysis/src/main/resources/pipeline-template-view-rules -name "*.json" -print0 | xargs -0L1 python -m json.tool

validate-resources: validate-report-view-rules validate-pipeline-view-rules

# e.g., make full-stress-run STRESS_RUNS=2
full-stress-run: test-data/smrtserver-testdata
	@for i in `seq 1 $(STRESS_RUNS)`; do \
	    RUN=run-$$(date +%s) && \
	    RUNDIR=test-output/stress-runs && \
	    OUTDIR=$$RUNDIR/$$RUN && \
	    mkdir -p $$OUTDIR && \
	    rm -f $$RUNDIR/latest && \
	    ln -s $$RUN $$RUNDIR/latest && \
	    rm -f smrt-server-analysis/db/analysis_services.db; \
	    rm -rf smrt-server-analysis/jobs-root/*; \
	    sbt smrt-server-analysis/compile && \
	    SERVERPID=$$(bash -i -c "sbt -no-colors \"smrt-server-analysis/run --log-file $(CURDIR)/$$OUTDIR/secondary-smrt-server.log\" > $$OUTDIR/smrt-server-analysis.out 2> $$OUTDIR/smrt-server-analysis.err & echo \$$!") && \
	    sleep 20 && \
	    ./stress.py --profile $$OUTDIR/profile.json > $$OUTDIR/stress.out 2> $$OUTDIR/stress.err ; \
	    sleep 2 ; \
	    pkill -g $$SERVERPID ; \
	    sleep 2 ; \
        done

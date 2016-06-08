SHELL=/bin/bash

clean:
	rm -f secondary-smrt-server*.log
	rm -rf smrt-server-analysis/{db,jobs-root}
	sbt clean

dataclean:
	rm -rf test-data

build: 
	sbt compile

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

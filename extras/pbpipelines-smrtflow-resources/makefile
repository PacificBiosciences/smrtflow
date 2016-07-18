PROJ_DIR := $(abspath $(lastword $(MAKEFILE_LIST)))

emit-pipelines:
	python custom_pipelines.py --log-level=INFO resolved-pipeline-templates

test-dev:
	source setup-env.sh && cd testkit-data && pbtestkit-multirunner --debug --nworkers 8 testkit.fofn

run-testkit: test-dev

test-pipelines:
	nosetests --verbose pbsmrtpipe.tests.test_pb_pipelines_sanity

test-loader:
	python -c "import pbsmrtpipe.loader as L; L.load_all()"

test-contracts:
	python -c "import pbsmrtpipe.loader as L; L.load_all()"

test-chunk-operators:
	python -c "import pbsmrtpipe.loader as L; L.load_and_validate_chunk_operators()"

test-sanity: test-contracts test-pipelines test-chunk-operators test-loader 

clean:
	find . -name "*.pyc" | xargs rm -rf
	find . -name "job_output" | xargs rm -rf
	find . -name "0.std*" -delete

emit-tool-contracts:
	smrtflow-example-tool emit-tc -o tool-contracts/smrtflow_example_tool_contract.json

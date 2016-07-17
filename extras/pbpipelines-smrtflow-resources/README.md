# pbpipeline-smrtflow-resources

SMRTFlow Pipeline resources leveraging smrtflow CLI tools/tasks.

Required Tools to run all the pipelines:

- [pbsmrtpipe](https://github.com/PacificBiosciences/pbsmrtpipe) (python)
- [pbcommand](https://github.com/PacificBiosciences/pbcommand) (python)
- [pbcoretools](https://github.com/PacificBiosciences/pbcoretools) (python)
- [smrtflow](https://github.com/PacificBiosciences/smrtflow) (scala)


Add custom tool contracts:

```
export PB_TOOL_CONTRACT_DIR=$(pwd)/tool-contracts
```


Add custom pipelines:

```
export PB_PIPELINE_TEMPLATE_DIR=$(pwd)/resolved-pipeline-templates
```

Or 

```
source setup-env.sh
```

## Emit Pipelines

```
make emit-pipelines
```


## Run Tests


Sanity and validity test of pipeline

```
make test-sanity
```

## Run Testkit jobs 

```
make run-testkit
```



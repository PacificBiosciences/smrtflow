# SMRT Server Analysis Internal

See [smrtflow.readthedocs.io](http://smrtflow.readthedocs.io/) for full docs and [smrtflow](../README.md) for the base multi-project's README. 

Adds support for running "Condition" driven pipelines via the "condition" job type and Condition JSON data model.

## Condition DataModel (WIP)


The model is both [pbcommand](https://github.com/PacificBiosciences/pbcommand/blob/master/pbcommand/models/conditions.py) and SLAI. It's not yet supported in pbcommandR.


### Resequencing Condition Type

Has both the SubreadSet, ReferenceSet and final AlignmentSet output:


```javascript
{
  "_condition_doc": "Example of a 'Resequencing' Condition Type",
  "conditions": [
    {
      "condId": "cond_alpha",
      "subreadset": "/path/to/subreadset-01.xml",
      "alignmentset": "/path/to/alignmentset-A.xml",
      "referenceset": "/path/to/reference.xml"
    },
    {
      "condId": "cond_alpha",
      "subreadset": "/path/to/subreadset-02.xml",
      "alignmentset": "/path/to/alignmentset-B.xml",
      "referenceset": "/path/to/reference.xml"
    },
    {
      "condId": "cond_beta",
      "subreadset": "/path/to/subreadset-03.xml",
      "alignmentset": "/path/to/alignmentset-C.xml",
      "referenceset": "/path/to/reference.xml"
    },
  ],
  "pipelineId": "pbsmrtpipe.pipelines.my_pipeline"
}

```


Initially only Reseq Condition type is supported

Considering an alternative model that has the job id, host, port as well as the resolved paths.

However, this would be increasing the surface area of the API and would be leaking unnecessary information. Tools should only operate on paths of files.

### Condition Based Tasks


An analysis will operate on a ConditionsList FileType and emit one or more reports. A condition list must contain the same file type.


```python
def analysis_example_main(analysis_conditions, output_json_report):
    # do stuff
    # this is just a basic form. Needs attributes table, or plot groups.
    r = Report("example", title="Example Report")
    r.write(output_json_report)
    return 0
```

The IO layer supports converting the JSON file.

Example:

```python
from pysiv2.internal.models import AnalysisConditions
c = AnalysisConditions.load_conditions_from("path-to-conditions.json")
exit_code = analysis_example_main(c, "output-report.json")
```


### Pipelines


Initial version will support a small number of pipelines that will approximate the functionality of the 'analysis groups' in legacy Milhouse stack. Each pipeline template will have a single input of a ConditionList FileType (e.g., FileTypes.COND). Each task in the pipeline will emit one or more FileTypes.REPORT(s). Tasks should be defined using the pbcommand TC API.

Example pipeline:

```python
@register_pipeline('pbinternal.pipelines.analysis_group_hello', "Simple HelloWorld Multi-Job analysis")
def bindings():
    b1 = [(Constants.CONDITION_EP, "pbinternal.tasks.analysis_readlength")]
    b2 = [(Constants.CONDITION_EP, 'pbinternal.tasks.analysis_yield")]
    return b1 + b2
```

Note pbinternal2 is currently not in the build. The dev/poc tools are currently in 'pysiv2.internal' to get around the lack of an internal build. 


### CSV Example file and Services


CSV file format is can be used to as an intermediate format to specify conditions from existing jobs that have been run.

The v1 hyper minimalist form is condition id, SMRT Link host and job id. The job **must** produce an alignmentset.

```csv
condId,host,jobId
condition_a,smrtlink-alpha,1234
condition_a,smrtlink-beta,456
condition_b,smrtlink-beta,789
```

The condition id and sub condtion id must be match [A-z0-9_], job_id must be an Int.

### Creating Condition Job from commandline


The job name, description, pipeline id and csv contents are posted to the "conditions" job endpoint.


```javascript
{
    "csvContents": "condId,host,jobId\ncond_a,smrtlink-beta,8554\ncond_a,smrtlink-beta,10159\ncond_b,smrtlink-alpha,151",
    "pipelineId": "pbsmrtpipe.pipelines.internal_cond_dev",
    "name": "Dev Job #1",
    "description": "Dev job for testing"
}
```


Example:

```bash

(core)smrtflow $> http post http://smrtlink-internal:8090/secondary-analysis/job-manager/jobs/conditions < example-condition-pipeline.json
HTTP/1.1 200 OK
Access-Control-Allow-Origin: *
Content-Encoding: gzip
Content-Length: 210
Content-Type: application/json; charset=UTF-8
Date: Thu, 19 May 2016 20:45:57 GMT
Server: spray-can/1.3.3

{
    "comment": "Condition Multi-Job",
    "createdAt": "2016-05-19T13:45:57.666-07:00",
    "id": 2,
    "jobTypeId": "conditions",
    "jsonSettings": "{}",
    "name": "Job conditions",
    "path": "",
    "state": "CREATED",
    "updatedAt": "2016-05-19T13:45:57.666-07:00",
    "uuid": "a1584178-30f7-4064-ac15-514089f9ba2d"
}

```

Note, the job type will be "pbsmrtpipe", not "conditions"

You can poll the job status using the standard model.


```bash

(core)smrtflow $> http get http://smrtlink-internal:8090/secondary-analysis/job-manager/jobs/a1584178-30f7-4064-ac15-514089f9ba2d
HTTP/1.1 200 OK
Access-Control-Allow-Origin: *
Content-Encoding: gzip
Content-Length: 281
Content-Type: application/json; charset=UTF-8
Date: Fri, 20 May 2016 00:03:21 GMT
Server: spray-can/1.3.3

{
    "comment": "Condition Multi-Job", 
    "createdAt": "2016-05-19T13:45:57.666-07:00", 
    "id": 2, 
    "jobTypeId": "conditions", 
    "jsonSettings": "{}", 
    "name": "Job conditions", 
    "path": "/pbi/dept/secondary/siv/smrtlink/smrtlink-internal/services_ui/smrtlink_services_ui-internal-0.7.2-180513/jobs-root/000/000002", 
    "state": "SUCCESSFUL", 
    "updatedAt": "2016-05-19T13:45:57.666-07:00", 
    "uuid": "a1584178-30f7-4064-ac15-514089f9ba2d"
}

```

See [Commandline Tools](https://github.com/PacificBiosciences/smrtflow/blob/master/smrt-server-analysis-internal/CLI.md) to help interface with the SLIA Services.
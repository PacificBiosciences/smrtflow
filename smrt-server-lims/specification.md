# LIMS API

V1 is a direct implementation of @mpkocher's gist:

https://gist.github.com/mpkocher/d2fc13e44336b1cf878b074fa3bb8869#file-LIMS_API-md

Once the code supports it, @jayson will improve this spec/docs help clarify the scope and use cases.

### LimsSubreadSet

```
GET /smrt-lims/lims-subreadset/{Subreadset-UUID} # Returns LimsSubreadSet Resource
GET /smrt-lims/lims-subreadset/{RUN-CODE}        # Returns LimsSubreadSet Resource
GET /smrt-lims/lims-subreadset/{experiment-id}   # Return List of LimsSubreadSet Resource or empty List if exp id isn't found
```

A query API isn't absolutely necessary in v1

Importing from `lims.yml`

```
POST /smrt-lims/lims-subreadset/import path=/path/to/lims.yml
```

TODO: Explicitly define LimsSubreadSet resource and sort out model to leverage `import-dataset` job type of SMRT Link.

## Resolver API

Resolving is a mechanism to provide a unique string id alias for a specific dataset by dataset type. Essentially, `name-id` is an alias to resolve a specific DataSet. These aliases can be used in batch pipeline submission (see below).

```
GET /smrt-lims/resolver/{dataset-type-short-name}/{name-id}
POST /smrt-lims/resolver/{dataset-type-short-name}/{UUID} name="name-id" 
DELETE /smrt-lims/resolver/{datasetyp-type-short-name}/{name-id} # "Unregister `name-id` to specific SubreadSet"
```
`dataset-type-short-name` is short name for the specific dataset type (e.g, references, subreads). This is consistent with the SMRT Link dataset API.

When creating an alias The DataSet UUID must be already imported and accessible via the standard SMRT Link when registring a new `name-id`

#### Examples

ReferenceSet is the primary usecase to enable automated batch submission

```
GET /smrt-lims/resolver/references/lambdaNeb
POST /smrt-lims/resolver/references/{UUID} name="lambdaNeb"
```

#### Batch Submission CSV

Only Resequencing-ish pipelines are supported (i.e., pipelines that have a SubreadSet and ReferenceSet as entry points)


##### CSV Fields

Required Fields

- name: Job Name
- description: Job Description
- runcode: SubreadSet runcode
- reference: ReferenceSet name-id
- pipeline_id: pbsmrtpipe resolved pipeline template id


TODO: Pipeline Template options. This should probably be enabled by Pipeline Preset template id, not by custom option ids.


Example CSV

```
name,description,runcode,reference,pipeline_id
job_a,My job A,3150007-0033,lambdaNeb,pbsmrtpipe.pipelines.sat
job_b,My job b,3150007-0034,lambdaNeb,pbsmrtpipe.pipelines.sat
```
# LIMS API

Adapted from [@mpkocher's gist](https://gist.github.com/mpkocher/d2fc13e44336b1cf878b074fa3bb8869#file-LIMS_API-md), but we still need to sort out expected use cases and a better spec.

Removed from the original spec is the idea of batch CSV submission. Example shell scripts do batch import via curl. Clarifying the use cases and spec for batch support would let it be part of v2 or whatever update we do next.

### LimsSubreadSet

```
GET /subreadset/{Subreadset-UUID} # Returns LimsSubreadSet Resource
GET /subreadset/{RUN-CODE}        # Returns LimsSubreadSet Resource
GET /subreadset/{experiment-id}   # Return List of LimsSubreadSet Resource or empty List if exp id isn't found
```

A query API isn't absolutely necessary in v1

Importing from `lims.yml`

```
POST /import # multipart/form-data with file containing lims.yml content
```

Creates a LimsSubreadSet with the searchable keys indexed. Eventually leverage `import-dataset` job type of SMRT Link.

## Resolver API

Resolving is a mechanism to provide a unique string id alias for a specific dataset by dataset type. Essentially, `name-id` is an alias to resolve a specific DataSet. These aliases can be used in batch pipeline submission (see below).

```
GET /resolver/{dataset-type-short-name}/{name-id}
POST /resolver/{dataset-type-short-name}/{UUID} name="name-id" 
DELETE /resolver/{datasetyp-type-short-name}/{name-id} # "Unregister `name-id` to specific SubreadSet"
```
`dataset-type-short-name` is short name for the specific dataset type (e.g, references, subreads). This is consistent with the SMRT Link dataset API.

When creating an alias The DataSet UUID must be already imported and accessible via the standard SMRT Link when registring a new `name-id`

#### Examples

ReferenceSet is the primary usecase to enable automated batch submission

```
GET /resolver/references/lambdaNeb
POST /resolver/references/{UUID}?name="lambdaNeb"
```
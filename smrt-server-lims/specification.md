# LIMS API

`smrt-lims` service spec. It is currently only partially complete because it supports `SubreadSet` files in a fashion that should be extensible to all `DataSet` types, but, currently, only `SubreadSet` is supported via `LimsSubreadSet`.

Table of Contents

- [LimsSubreadSet](#LimsSubreadSet) ([API](#LimsSubreadSet_API), [Resolver API](#LimsSubreadSet_Resolver_API))

## Changelog

- 2016-07-27 v1 from [smrtflow#259](https://github.com/PacificBiosciences/smrtflow/pull/259) adapted from [@mpkocher's gist](https://gist.github.com/mpkocher/d2fc13e44336b1cf878b074fa3bb8869#file-LIMS_API-md)

## LimsSubreadSet

This is a superset of values from `.subreadset.xml` to included some data
from `lims.yml`. Currently, these files all live in `/pbi/collections`
and there should be a one-to-one mapping from a movie context files to
a (`lims.yml`, `*.subreadset.xml` file in the same directory. 

Extracted values in `LimsSubreadSet` are as follows.
- From `lims.yml`
  - runcode (lims.yml)
  - experiment id (lims.yml)
- From `.subreadset.xml`
  - UUID
  - pa version (aka Primary Analysis software version)
  - ics version (aka Instrument Control Software version)
  - # TODO: clarify what MK means by this one, "SubreadServiceDataSet (see model in smrtflow, this will give it parity with the SMRT Link and SMRT Link Analysis DataSet services)"

Some complications exist with relying on the `/pbi/collections` files and
these assumptions are used.

- If there is no `.subreadset.xml` that matches a `lims.yml` then the import will fail.
- A lookup must be done to guess the appropriate `.subreadset.xml`. 
    - If a file exists that matches the movie context name, that is used.
    - Otherwise, the first found `.subreadset.xml` in that directory is used.

### LimsSubreadSet API

```
GET /smrt-lims/subreadset/{Subreadset-UUID} # Returns LimsSubreadSet Resource
GET /smrt-lims/subreadset/{RUN-CODE}        # Returns LimsSubreadSet Resource
GET /smrt-lims/subreadset/{experiment-id}   # Return List of LimsSubreadSet Resource or empty List if exp id isn't found
```

A query API isn't absolutely necessary in v1

Importing from `lims.yml`

```
POST /smrt-lims/import # multipart/form-data with file containing lims.yml content
```

Creates a LimsSubreadSet with the searchable keys indexed. Eventually leverage `import-dataset` job type of SMRT Link.

### LimsSubreadSet Resolver API

Resolving is a mechanism to provide a unique string id alias for a specific dataset by dataset type. Essentially, `name-id` is an alias to resolve a specific DataSet. These aliases can be used in batch pipeline submission (see below).

```
GET /smrt-lims/resolver/{dataset-type-short-name}/{name-id}
POST /smrt-lims/resolver/{dataset-type-short-name}/{UUID} name="name-id" 
DELETE /smrt-lims/resolver/{datasetyp-type-short-name}/{name-id} # "Unregister `name-id` to specific SubreadSet"
```
`dataset-type-short-name` is short name for the specific dataset type (e.g, references, subreads). This is consistent with the SMRT Link dataset API.

When creating an alias The DataSet UUID must be already imported and accessible via the standard SMRT Link when registring a new `name-id`

### LimsSubreadSet Examples

See the `RouteImportAndResolveSpec` for examples of using the API and RESTful endpoints.
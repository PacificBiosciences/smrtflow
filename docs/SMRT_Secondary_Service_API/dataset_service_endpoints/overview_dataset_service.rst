Overview of Dataset Service
===========================

Use Cases
---------

The Dataset Service enables accessing datasets.

The Dataset Service provides an API for retrieving datasets.

The Dataset Service does not provide an API for creating datasets,
because datasets get created through the Jobs Service,
where users may create new datasets from scratch using import-dataset job type,
or may create datasets from other datasets using various analysis jobs.

The Dataset Service does not provide an API for updating datasets,
because datasets are supposed to be immutable.

Key Concepts
------------

A **DataSet** is defined as a set of a fundamental data type (e.g. subreads)
that for some analyses can be processed in the same way (same pipeline, same parameters).
A common example would be a group of reads which correspond to a DNA sample.

Definition of a dataset depends on the exact data type being manipulated.
For example, defining SubreadSet (a DataSet of subreads) would require the following information:

-  The location of cells and optionally portions of cells (e.g. cells +
   barcode) to include in the dataset;

-  Data metadata, such as sample name, comments, owner, permissions.

Dataset Types
~~~~~~~~~~~~~

Dataset Types list:

-  alignments

-  barcodes

-  contigs

-  ccsalignments

-  ccsreads

-  hdfsubreads

-  references

-  subreads


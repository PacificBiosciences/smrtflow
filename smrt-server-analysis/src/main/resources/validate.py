#!/usr/bin/python

import glob
from json import load

json_schema_files = glob.glob("*_schema.json")
for json_schema_file in json_schema_files:
    print "Validating %s" % json_schema_file
    load(open(json_schema_file))

from jsonschema import validate

json_example_files = glob.glob("*_example.json")
for json_example_file in json_example_files:
    json_schema_file = json_example_file.replace("_example", "_schema")
    print "Validating %s" % json_example_file
    example_json = load(open(json_example_file))
    example_schema = load(open(json_schema_file))
    validate(example_json, example_schema)

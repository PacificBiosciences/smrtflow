import os
import json
import logging
import random
import collections
import hashlib
import uuid
import sys

from xmlbuilder import XMLBuilder
from functools import partial

log = logging.getLogger(__name__)

REGISTRY = {}

DATASET_METADATA = ["tags",
                    "comments",
                    "createdAt",
                    "updatedAt",
                    "status",
                    "md5",
                    "parentJobId",
                    "userId",
                    "serviceTags"]

# The actual DataSet will be the
DATASET_METADATA.append("dataset")

DataSetMetaData = collections.namedtuple('DataSetMetaData', DATASET_METADATA)


def get_core_registry_type():
    def to_int(max_int=100):
        return random.randint(0, max_int)

    def to_string():
        return "Random string"

    def to_datetime():
        # iso8601
        return "2014-10-24T19:17:30+00:00"

    def to_uuid():
        return str(uuid.uuid4())

    def to_tags():
        n = random.randint(1, 5)
        all_tags = 'mytags moreTags mySpecial-Tags filter mapping barcode'.split()
        tags = {random.choice(all_tags) for _ in xrange(n)}
        return list(tags)

    return {'integer': to_int, 'string': to_string, 'date-time': to_datetime,
            'UUID': to_uuid, 'array': to_tags}


def get_core_tytpe_to_case_class():
    def to_int():
        return "Int"

    def to_string():
        return "String"

    def to_uuid():
        return "java.util.UUID"

    def to_datetime():
        return "JodaDateTime"

    def to_array():
        return "Seq[String]"

    return {'integer': to_int, 'string': to_string, 'UUID': to_uuid,
            'date-time': to_datetime, 'array': to_array}


def to_md5(s=None):
    x = "a string" if s is None else s
    return hashlib.md5(x).hexdigest()


def to_cell_ids():
    return [ "%i" % random.randint(1, 1000000) 
                for i in range(random.randint(1,10)) ]


def to_name(prefix="name"):
    return "%s_%i" % (prefix, random.randint(1,1000))


def to_version():
    n = random.randint(1, 5)
    return "0.{n}.0".format(n=n)


def to_chemistry():
    return random.choice("XL-C2 P4-C2 P5-C3 P6-C4 [Multiple]".split(" "))


def get_default_overrides():
    """
    Add custom funcs here based on the property name
    """
    d = {}
    d['md5']            = to_md5
    d['version']        = to_version
    d['cellIds']        = to_cell_ids
    d['sampleName']     = partial(to_name, "sample")
    d['plateName']      = partial(to_name, "plate")
    d['runName']        = partial(to_name, "run")
    d['wellSampleName'] = partial(to_name, "well_sample")
    d['instrumentName'] = partial(to_name, "instrument")
    d['primaryVersion'] = to_version
    d['chemistry']      = to_chemistry
    return d


def to_named_tuple_from_schema(schema_dict, dataset_class_name, core_type_key_to_func_d, override_key_to_func_d=None):
    """

    :param schema_dict: JsonSchema dict
    :param dataset_class_name: Name of the namedtype class that will be created
    :param core_type_key_to_func_d: {json_type:func} with the func having no args. (json_type e.g., 'string', 'integer')
    :param override_key_to_func_d: Custom override for {propertyName:func} e.g., {'cellId': myFunc}
    """

    properties = schema_dict['properties']

    if override_key_to_func_d is None:
        override_key_to_func_d = {}

    attr_names = properties.keys()

    try:
        klass = collections.namedtuple(dataset_class_name, attr_names)
    except ValueError:
        sys.stderr.write("Unable to create {x} named tuple from attrs {d}\n".format(d=attr_names, x=dataset_class_name))
        raise

    attrs = {}
    for attr_name, type_schema in properties.iteritems():
        custom_func = override_key_to_func_d.get(attr_name, None)
        if custom_func is None:
            attr_type = type_schema['type']
            func = core_type_key_to_func_d[attr_type]
            attrs[attr_name] = func()
        else:
            attrs[attr_name] = custom_func()

    return klass(**attrs)


def schema_to_case_class(schema_dict, case_class_name, core_type_to_func_d):
    """Convert a schema a to a scala case class"""
    properties = schema_dict['properties']
    # so the mock data can be added
    attr_names = sorted(properties.keys())

    outs = []
    for attr_name in attr_names:
        type_schema = properties[attr_name]
        attr_type = type_schema['type']
        scala_type = core_type_to_func_d[attr_type]()
        outs.append(":".join([attr_name, scala_type]))

    s = ", \n".join(outs)
    return "".join(['case class {x}('.format(x=case_class_name), s, ")"])


def to_case_class(schema_file, case_class_name):
    d = get_core_tytpe_to_case_class()
    schema_d = schema_file_to_dict(schema_file)
    return schema_to_case_class(schema_d, case_class_name, d)


def example_to_case_class():
    f = 'dataset_subread_schema.json'
    class_name = "SubreadDataSet"
    return to_case_class(f, class_name)


def schema_file_to_dict(schema_file_name):
    with open(schema_file_name, 'r') as r:
        schema_d = json.loads(r.read())
    return schema_d


def builder(schema_file_name, class_name):

    schema_d = schema_file_to_dict(schema_file_name)

    def _to_cell_id():
        return "Cell id. X"

    override_d = get_default_overrides()
    override_d['cellId'] = _to_cell_id

    core_registry_types = get_core_registry_type()
    return to_named_tuple_from_schema(schema_d, class_name, core_registry_types, override_d)


def example_subreads():
    f = 'dataset_subread_schema.json'
    class_name = "SubreadDataSet"
    return builder(f, class_name)


def _to_class_name_from_schema_name(schema_file_name):
    s = os.path.basename(schema_file_name)
    h, _ = os.path.splitext(s)
    a = h.split('_')[1]
    return a.capitalize() + "DataSet"


def named_tuple_to_dict(n):
    return dict(n.__dict__)


def example_all():
    base_dir = os.getcwd()
    json_files = [os.path.join(base_dir, f) for f in os.listdir(base_dir) if f.endswith('_schema.json')]
    datasets = {}

    for json_file in json_files:
        class_name = _to_class_name_from_schema_name(json_file)
        s = builder(json_file, class_name)
        datasets[class_name] = s

    return datasets


def to_subread_ds_xml(ds):
    x = XMLBuilder('dataset', id=ds.id,
                   datasetTypeId="pacbio.datasets.subread_dataset")

    with x.metadata:
        x.createdAt(ds.createdAt)
        x.version(ds.version)
        x.name(ds.name)
        x.numRecords("1234")
        x.totalLength("12345")

        with x.dataset:
            x.cellId(str(ds.cellId))
            x.numCells(str(ds.numCells))

    return x

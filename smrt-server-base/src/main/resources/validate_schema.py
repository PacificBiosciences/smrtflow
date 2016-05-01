#!/usr/bin/env python
import os
import sys
import logging
import argparse
import json
import jsonschema


log = logging.getLogger(__name__)


def _validate_file(p):
    if os.path.isfile(p):
        return os.path.abspath(p)
    raise IOError("Unable to find '{p}''".format(p=p))


def get_parser():
    p = argparse.ArgumentParser(description="Validate JSONSchema file (v4)")
    p.add_argument("schema_path", type=_validate_file, help="Path to schema")
    p.add_argument('--debug', action='store_true', help="Verbose output to stdout.")
    return p

def runner(schema_path):
    with open(schema_path, 'r') as f:
        schema = f.read()
    jschema = json.loads(schema)
    log.debug(jschema)
    _ = jsonschema.Draft4Validator(jschema)
    msg = "Schema {f} is valid".format(f=os.path.basename(schema_path))
    print msg
    return 0


def _runner(args):
    if args.debug:
        logging.basicConfig(level=logging.DEBUG)
        log.debug(args)

    return runner(args.schema_path)


def main():
    p = get_parser()
    p.set_defaults(func=_runner)
    args = p.parse_args()
    return args.func(args)


if __name__ == '__main__':
    sys.exit(main())

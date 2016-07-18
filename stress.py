#!/usr/bin/env python
"""

Template taken from: https://github.com/PacificBiosciences/pbcommand/blob/master/pbcommand/cli/examples/template_simple.py
"""

import os
import sys
import json
import logging
import multiprocessing
import time
import uuid as U
import xml.dom.minidom

import requests

from pbcommand.cli import (get_default_argparser_with_base_opts, pacbio_args_runner)

from pbcommand.engine import run_cmd, ExtCmdResult
from pbcommand.services import ServiceAccessLayer
from pbcommand.utils import setup_log

from pbcore.io.dataset import openDataSet

log = logging.getLogger(__name__)

__version__ = "0.1.4"


def _process_original_dataset(path, output_dir_prefix):
    # copy to new output dir
    # change the UUID
    basename = os.path.basename(path)

    # overwrite the UUID
    ds_uuid = U.uuid4()

    output_dir = os.path.join(output_dir_prefix, "dataset-{x}".format(x=ds_uuid))
    os.mkdir(output_dir)

    # expected output path
    dataset_xml = os.path.join(output_dir, basename)

    cmd = "dataset copyto {i} {o}".format(i=path, o=output_dir)
    _ = _run_cmd(cmd)

    ds = openDataSet(dataset_xml)

    # this is confusing, but the dataset will need a random name
    # to break the hashing which generate the same UUID
    ds.name = "New-dataset-{u}".format(u=ds_uuid)

    # this uses the "core" attributes which will generate the uuid
    ds.newUuid()

    ds.write(dataset_xml)
    log.info("write dataset {u} to {p}".format(u=ds_uuid, p=dataset_xml))
    return dataset_xml


def _run_cmd(cmd):
    x = run_cmd(cmd, sys.stdout, sys.stderr)
    if x.exit_code != 0:
        log.error(x)
    return x


FUNCS = {}


def register(f):
    FUNCS[f.__name__] = f
    return f


def runner(args, **kwargs):
    fname = args[0]
    f = FUNCS[fname]
    log.debug("Running {f} {a} {k}".format(f=f, a=args[1:], k=kwargs))
    return f(*args[1:], **kwargs)


@register
def get_status(host, port):
    return _run_cmd("pbservice status --host={h} --port={p}".format(h=host, p=port))


@register
def import_dataset(host, port, original_dataset_path, output_dir_prefix):
    new_dataset = _process_original_dataset(original_dataset_path, output_dir_prefix)
    ds = openDataSet(new_dataset)
    log.info("Importing dataset {u} -> {p}".format(u=ds.uuid, p=new_dataset))
    return _run_cmd("pbservice import-dataset --host={h} --port={p} {x}".format(h=host, p=port, x=new_dataset))


@register
def add_run_design(host, port, run_design_path):
    start_time = time.time()

    run_design = xml.dom.minidom.parse(run_design_path)

    run_elem = run_design.getElementsByTagName("Run")[0]
    run_elem.attributes["UniqueId"].value = str(U.uuid4())

    for subreadset in run_design.getElementsByTagName("SubreadSet"):
        subreadset.attributes["UniqueId"].value = str(U.uuid4())

    url = "http://{h}:{p}/smrt-link/runs".format(h=host,p=port)
    response = requests.post(url, json={"dataModel": run_design.toxml()})

    exit_code = 0 if response.status_code == 201 else response.status_code
    return ExtCmdResult(exit_code,
                        "POST {url}".format(url=url),
                        time.time() - start_time)


@register
def run_analysis(host, port, path):
    return _run_cmd("pbservice run-analysis --host={h} --port={p} --block {x}".format(h=host, p=port, x=path))


def _generate_data(host, port, dataset_paths, analysis_json,
                   run_design_path, output_dir_prefix, ntimes):
    for x in xrange(ntimes):
        yield "get_status", host, port
        for dataset_path in dataset_paths:
            yield "import_dataset", host, port, dataset_path, output_dir_prefix
        yield "add_run_design", host, port, run_design_path
        yield "get_status", host, port
        yield "run_analysis", host, port, analysis_json


def get_parser():

    p = get_default_argparser_with_base_opts(__version__, "Stress Tool")
    f = p.add_argument
    f('--host', default="localhost", help="Host name")
    f('--port', default=8070, type=int, help="Port")
    f('-n', '--nprocesses', default=10, type=int, help="Number of worker processes to launch")
    # FIXME this naming is terrible
    f('-x', default=5, type=int, help="Total number of tasks will be ~ 3 x")
    f('--profile', default="profile.json", help="Path to output profile.json")
    return p


def write_profile(d, output_file):
    with open(output_file, 'w') as f:
        f.write(json.dumps(d, indent=4, sort_keys=True))
    return d


def run_main(host, port, nprocesses, ntimes, profile_csv):
    # logging.basicConfig(level=logging.DEBUG, file=sys.stdout)

    profile_d = {}

    started_at = time.time()

    log.info(FUNCS.keys())

    sal = ServiceAccessLayer(host, port)
    status = sal.get_status()
    log.info("Status {}".format(status))

    profile_d['nprocesses'] = nprocesses
    profile_d["init_nsubreads"] = len(sal.get_subreadsets())
    profile_d['init_nreferences'] = len(sal.get_referencesets())
    profile_d['init_njobs'] = len(sal.get_analysis_jobs())

    chunksize = 6

    info = "{h}:{p} with ntimes:{n} with processors:{x}".format(h=host, p=port, n=ntimes, x=nprocesses)

    # FIXME. All paths are relative to smrtflow root
    def to_p(rpath):
        return os.path.join(os.getcwd(), rpath)

    # DataSet
    referenceset_path = to_p("test-data/smrtserver-testdata/ds-references/mk-01/mk_name_01/referenceset.xml")
    subreadset_path = to_p("test-data/smrtserver-testdata/ds-subreads/lambda/2372215/0007_micro/0007_micro/Analysis_Results/subreads.xml")

    # Run Design
    run_design_path = to_p("smrt-server-link/src/test/resources/runCreate2.xml")

    # Dev Diagnostic
    analysis_json = to_p("smrt-server-analysis/src/test/resources/analysis-dev-diagnostic-01.json")

    output_dir_prefix = to_p("test-output")
    if not os.path.exists(output_dir_prefix):
        os.mkdir(output_dir_prefix)

    # import referenceset with original UUID for the dev_diagnostic run
    _run_cmd("pbservice import-dataset --host={h} --port={p} {x}".format(h=host, p=port, x=referenceset_path))

    xs = _generate_data(host, port, [referenceset_path, subreadset_path],
                        analysis_json, run_design_path, output_dir_prefix, ntimes)

    log.info("Starting {i}".format(i=info))

    p = multiprocessing.Pool(nprocesses)

    results = p.map(runner, xs, chunksize=chunksize)

    failed = [r for r in results if r.exit_code != 0]
    was_successful = len(failed) == 0
    for f in failed:
        log.error(f)

    log.debug("exiting {i}".format(i=info))
    if failed:
        log.error("Failed Results {r} of {x}".format(r=len(failed), x=len(results)))

    run_time_sec = time.time() - started_at

    profile_d['nresults'] = len(results)
    profile_d['nfailed'] = len(failed)
    profile_d['was_successful'] = was_successful

    profile_d["final_nsubreads"] = len(sal.get_subreadsets())
    profile_d['final_nreferences'] = len(sal.get_referencesets())
    profile_d['final_njobs'] = len(sal.get_analysis_jobs())
    profile_d['run_time_sec'] = run_time_sec

    write_profile(profile_d, profile_csv)
    return 0 if was_successful else 1


def args_runner(args):
    return run_main(args.host, args.port, args.nprocesses, args.x, args.profile)


def main(argv):
    return pacbio_args_runner(argv[1:],
                              get_parser(),
                              args_runner,
                              log,
                              setup_log_func=setup_log)


if __name__ == '__main__':
    sys.exit(main(sys.argv))

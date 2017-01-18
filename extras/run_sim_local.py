#!/usr/bin/env python

# TODO rewrite me in Scala

"""
Wrapper script to start the analysis server and run a simulator scenario.

This should be called from that makefile via test-sim.

Assumptions:

- The working directory is assumed to be the root smrtflow repo directory.
- PB_TEST_DATA_DIR env var exists, or the PacBioTestData package is installed
- pbreports (optional) install
- Database is completely EMPTY

Drop Database using

$> dropdb smrtlink

Create Database using

$> createdb smrtlink

"""

import subprocess
import threading
import argparse
import tempfile
import logging
import shutil
import signal
import os.path as op
import os
import sys
import json

ROOT_DIR = op.dirname(op.dirname(op.abspath(__file__)))
TARGET_DIR = ROOT_DIR + "/smrt-server-analysis/target"
SIM_RUNNER = ROOT_DIR + "/smrt-server-sim/target/pack/bin/scenario-runner"

log = logging.getLogger(__name__)

__version__ = "0.2.1"

MANIFEST = """
[{
  "name": "SMRT Link",
  "description": "SMRT Link (smrtlink-incremental_3.2.0.184296,184296-184240-184167-184167-184167)",
  "version": "3.2.0.184296",
  "id": "smrtlink",
  "dependencies": []
}, {
  "name": "SMRT Tools",
  "description": "SMRT Tools (smrttools-incremental_3.2.0.184296)",
  "version": "3.2.0.184296",
  "id": "smrttools",
  "dependencies": []
}]
"""

class ServiceManager(object):
    """
    Context manager for the server process
    """

    def __init__(self, path_to_conf):
        # Custom application.conf
        self.path_to_conf = path_to_conf
        self._t = threading.Thread(target=self._run)
        self.jar_file = None
        for dir_name in os.listdir(TARGET_DIR):
            if dir_name.startswith("scala-"):
                for file_name in os.listdir(op.join(TARGET_DIR, dir_name)):
                    if (file_name.startswith("smrt-server-analysis-assembly")
                        and file_name.endswith(".jar")):
                        self.jar_file = op.join(TARGET_DIR, dir_name, file_name)
                        break
                else:
                    continue
                break
        if self.jar_file is None:
            raise RuntimeError("Can't find assembly jar file")
        log.info("Server jar file is {j}".format(j=self.jar_file))

    def _run(self):
        with open("smrt-server-analysis.out", "w") as out:
            with open("smrt-server-analysis.err", "w") as err:
                log.info("Starting server")
                cmd = ["java", '-Dconfig.file={}'.format(self.path_to_conf), "-jar", self.jar_file]
                log.info("Starting server with command '{}'".format(" ".join(cmd)))
                self._p = subprocess.Popen(cmd, stdout=out, stderr=err)

    def __enter__(self):
        self._t.start()
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        # XXX I wouldn't expect this to be thread-safe, but it doesn't crash...
        self._p.kill()
        self._t.join()


# http://stackoverflow.com/a/1191537
class Alarm(Exception):
    pass


def _alarm_handler(signum, frame):
    raise Alarm


def get_parser():
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    p.add_argument("scenario", help="Pacbio Scenario id (example, DataSetScenario)")

    p.add_argument("--local", action="store_true",
                   help="Run in current directory instead of tmp dir")
    p.add_argument("--stay-alive", action="store_true",
                   help="Leave services running after simulator exits")
    p.add_argument("--max-workers", action="store", default=8,
                   help="Max. number of engine job workers")
    p.add_argument("--njobs", action="store", default=50,
                   help="Number of simultaneous jobs in StressTest")
    p.add_argument("--max-wait", action="store", default=300,
                   help="Maximum time to wait for pbsmrtpipe jobs")
    p.add_argument("--timeout", action="store", type=int, default=None,
                   help="Kill simulator after X seconds if not finished")
    p.add_argument("--debug", action="store_true", default=False,
                   help="Enable debug mode and not delete temp resources")

    p.add_argument("--port", default=8071, type=int, help="Port to launch SL Analysis services on")
    return p


def to_conf_d(host, port, njobs, max_wait, max_workers, manifest_path, pb_test_files_json):
    """Generate a application.conf formatted string"""

    server = {"host": host, "port": port, "manifest-file": manifest_path}
    engine = {"max-workers": max_workers}
    test = {"njobs": njobs, "max-time": max_wait, "test-files": pb_test_files_json}
    conf_d = dict(smrtflow=dict(server=server, engine=engine, test=test))
    return conf_d


def get_pb_test_files():
    """ Return the path to PacBioTestData files.json or raise"""
    x = "PB_TEST_DATA_FILES"
    try:
        import pbtestdata
        return pbtestdata.get_path()
    except ImportError:
        log.info("Attempting to get PacBioTestData files.json path from ENV var {}".format(x))
        path = os.environ.get(x)
        if path is None:
            raise RuntimeError("Unable to determine PacBioTestData files.json path. ENV var {} is not defined and pbtestdata module is not installed.".format(x))
        resolved_path = op.abspath(path)
        if op.isfile(resolved_path):
            return resolved_path
        raise IOError("Unable to find PacBioTestData files.json from {}".format(resolved_path))


def run(argv):
    logging.basicConfig(level=logging.INFO)
    p = get_parser()
    args = p.parse_args(argv)

    port = args.port
    debug = args.debug
    delete_on_exit = not debug

    if not op.exists(SIM_RUNNER):
        raise RuntimeError("'{s}' exe not found".format(s=SIM_RUNNER))

    pb_test_files_json = get_pb_test_files()

    manifest = tempfile.NamedTemporaryFile(suffix="_manifest.json", delete=delete_on_exit).name
    with open(manifest, "w") as mout:
        mout.write(MANIFEST)

    test_conf = tempfile.NamedTemporaryFile(suffix="_conf.json", delete=delete_on_exit).name
    # this should probably try to use as many non-default values as possible to make sure they're set correctly
    conf_d = to_conf_d("localhost", port, args.njobs, args.max_wait, args.max_workers, manifest, pb_test_files_json)

    log.info("application_conf.json for Sim {}".format(test_conf))
    log.info(conf_d)
    with open(test_conf, "w+") as w:
        w.write(json.dumps(conf_d, indent=True))

    log.info("Manifest file is {f}".format(f=manifest))

    cwd = os.getcwd()
    tmp_dir = None
    if not args.local:
        tmp_dir = tempfile.mkdtemp()
        log.info("Will run in {d}".format(d=tmp_dir))
        os.chdir(tmp_dir)
    else:
        if op.exists("db") or op.exists("jobs-root"):
            raise RuntimeError("Please remove 'db' and 'jobs-root' directories")
    rc = 0
    try:
        with ServiceManager(test_conf) as server:
            log.info("Running simulator scenario...")
            signal.signal(signal.SIGALRM, _alarm_handler)
            if args.timeout is not None and args.timeout > 0:
                signal.alarm(args.timeout)
            try:
                cmd = [SIM_RUNNER, args.scenario, test_conf]
                log.info("Calling Sim with exe '{}'".format(" ".join(cmd)))
                rc = subprocess.call(cmd)
                signal.alarm(0)
            except Alarm:
                log.error("Simulator did not terminate, killed")
            else:
                if rc != 0:
                    log.error("Simulator scenario failed")
            if args.stay_alive:
                log.warn("Leaving services running - control-C to exit")
                while True:
                    pass
    finally:
        if not debug:
            log.info("Cleaning up temporary files")
            if tmp_dir is not None:
                os.chdir(cwd)
                shutil.rmtree(tmp_dir)
            os.remove(test_conf)
        else:
            log.info("running in debug mode. Not deleting tmp files")

    log.info("Exiting {f} {v} with exit code {e}".format(f=__file__, v=__version__, e=rc))
    return rc

if __name__ == "__main__":
    sys.exit(run(sys.argv[1:]))

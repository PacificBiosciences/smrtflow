#!/usr/bin/env python

# TODO rewrite me in Scala

"""
Wrapper script to start the analysis server and run a simulator scenario.
"""

import subprocess
import threading
import argparse
import tempfile
import logging
import shutil
import os.path as op
import os
import sys

ROOT_DIR = op.dirname(op.dirname(op.abspath(__file__)))
TARGET_DIR = ROOT_DIR + "/smrt-server-analysis/target"
SIM_RUNNER = ROOT_DIR + "/smrt-server-sim/target/pack/bin/scenario-runner"

log = logging.getLogger(__name__)


class ServiceManager(object):
    """
    Context manager for the server process
    """

    def __init__(self):
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
                self._p = subprocess.Popen(["java", "-jar", self.jar_file],
                                           stdout=out, stderr=err)

    def __enter__(self):
        self._t.start()
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        # XXX I wouldn't expect this to be thread-safe, but it doesn't crash...
        self._p.kill()
        self._t.join()


def run(argv):
    logging.basicConfig(level=logging.INFO)
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("scenario")
    p.add_argument("--local", action="store_true",
                   help="Run in current directory instead of tmp dir")
    p.add_argument("--stay-alive", action="store_true",
                   help="Leave services running after simulator exits")
    p.add_argument("--max-workers", action="store", default=8,
                   help="Max. number of engine job workers")
    p.add_argument("--njobs", action="store", default=50,
                   help="Number of simultaneous jobs in StressTest")
    p.add_argument("--max-time", action="store", default=300,
                   help="Maximum time to wait for pbsmrtpipe jobs")
    args = p.parse_args(argv)
    test_conf = tempfile.NamedTemporaryFile(suffix=".conf").name
    with open(test_conf, "w") as conf:
        conf.write("smrt-link-host = \"localhost\"\n")
        conf.write("smrt-link-port = 8070\n")
        conf.write("njobs = {n}\n".format(n=args.njobs))
        conf.write("max-time = {t}\n".format(t=args.max_time))
    log.info("Capping number of workers at {j}".format(j=args.max_workers))
    os.environ["PB_ENGINE_MAX_WORKERS"] = str(args.max_workers)
    try:
        import pbtestdata
    except ImportError:
        if not "PB_TEST_DATA_FILES" in os.environ:
            raise RuntimeError(
                "Missing PB_TEST_DATA_FILES or pbtestdata module")
    else:
        os.environ["PB_TEST_DATA_FILES"] = pbtestdata.get_path()
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
        with ServiceManager() as server:
            log.info("Running simulator scenario...")
            rc = subprocess.call([SIM_RUNNER, args.scenario, test_conf])
            if args.stay_alive:
                log.warn("Leaving services running - control-C to exit")
                while True:
                    pass
    finally:
        log.info("Cleaning up temporary files")
        if tmp_dir is not None:
            os.chdir(cwd)
            shutil.rmtree(tmp_dir)
        os.remove(test_conf)
    return rc

if __name__ == "__main__":
    sys.exit(run(sys.argv[1:]))

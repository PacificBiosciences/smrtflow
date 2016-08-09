#!/usr/bin/env python

"""
Wrapper script to start the analysis server and run a simulator scenario.
"""

import subprocess
import threading
import argparse
import tempfile
import shutil
import os.path as op
import os
import sys

ROOT_DIR = op.dirname(op.dirname(op.abspath(__file__)))
JAR_FILE = ROOT_DIR + \
    "/smrt-server-analysis/target/scala-2.11/smrt-server-analysis-assembly-0.1.5-SNAPSHOT.jar"
GET_STATUS = ROOT_DIR + "/smrt-server-base/target/pack/bin/get-smrt-server-status"
SIM_RUNNER = ROOT_DIR + "/smrt-server-sim/target/pack/bin/scenario-runner"


class ServiceManager(object):
    """
    Context manager for the server process
    """

    def __init__(self):
        self._t = threading.Thread(target=self._run)

    def _run(self):
        with open("smrt-server-analysis.out", "w") as out:
            with open("smrt-server-analysis.err", "w") as err:
                self._p = subprocess.Popen(["java", "-jar", JAR_FILE],
                                           stdout=out, stderr=err)

    def __enter__(self):
        self._t.start()
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        self._p.kill()
        self._t.join()


def run(argv):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("scenario")
    p.add_argument("--local", action="store_true",
                   help="Run in current directory instead of tmp dir")
    args = p.parse_args(argv)
    test_conf = tempfile.NamedTemporaryFile(suffix=".conf").name
    with open(test_conf, "w") as conf:
        conf.write("smrt-link-host = \"localhost\"\n")
        conf.write("smrt-link-port = 8070\n")
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
        os.chdir(tmp_dir)
    else:
        if op.exists("db") or op.exists("jobs-root"):
            raise RuntimeError("Please remove 'db' and 'jobs-root' directories")
    try:
        with ServiceManager() as server:
            assert subprocess.call([GET_STATUS, "--host", "localhost",
                                    "--port", "8070",
                                    "--max-retries", "8"]) == 0
            assert subprocess.call([SIM_RUNNER, args.scenario, test_conf]) == 0
    finally:
        if tmp_dir is not None:
            os.chdir(cwd)
            shutil.rmtree(tmp_dir)
    return 0

if __name__ == "__main__":
    sys.exit(run(sys.argv[1:]))

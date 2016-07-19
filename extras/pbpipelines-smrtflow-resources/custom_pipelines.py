#!/usr/bin/env python
"""
Example for defining Custom Pipelines
using pipelines to emit a Pipeline XML or ResolvedPipeline Template JSON file
"""
import logging
import sys

from pbsmrtpipe.core import PipelineRegistry
from pbsmrtpipe.cli_custom_pipeline import registry_runner_main

# This should probably be removed
from pbsmrtpipe.pb_pipelines.pb_pipelines_sa3 import Constants, Tags

log = logging.getLogger(__name__)


class C(object):
    PT_NAMESPACE = "smrtflow_dev"


registry = PipelineRegistry(C.PT_NAMESPACE)


def _example_topts():
    return {"smrtflow.task_options.num_records": 60}


@registry("example_01", "Custom Example 01", "0.1.0", tags=("dev", "smrtflow"), task_options=_example_topts())
def to_bs():
    """Custom Pipeline Registry for dev hello world tasks"""
    b1 = [('$entry:e_01', 'smrtflow.tasks.example_tool:0')]

    return b1


if __name__ == '__main__':
    sys.exit(registry_runner_main(registry)(argv=sys.argv))

# pbservices Bundle


ScratchPad for Service Bundle API

The templates use python's stdlib Template library syntax ${MY_VAR}.

By convention all vars are upper cased.



# Building Bundles

Bundles that can be built

```
$> fab -l
```

Example.

```
$> fab build_smrtlink_services_ui:"0.2.1","/Users/mkocher/workspaces/mk_mb_pbbundler/ui","/Users/mkocher/workspaces/mk_mb_pbbundler/scala"

```

Nightly builds are published to "/mnt/secondary/Share/smrtserver-bundles-nightly". These should only be consumed by secondary developers.

"Gold" builds are published to "/mnt/secondary/Share/smrtserver-bundles". These are builds that are intended to be used by other teams, such as ICS, or Primary.

# Building with artifacts in bamboo

The original bundler model had the bundler doing multiple things:

* compiling code
* copying compilation outputs to their destination directories
* template processing
* installing python modules

That meant that separate codebases (mainly ui and smrtflow) were getting compiled on every bundler run.

In bamboo, the bundler can be configured to run when any one of its upstream components (ui, smrtflow, pbcommand, pbcore, pbreports, pbsmrtpipe) changes.  That results in an up-to-date bundle, which is nice.  But if we're compiling ui and smrtflow in every bundler run, then that's redundant work for every bundler run where ui and/or smrtflow haven't changed.

Instead, we should move the ui and smrtflow compile steps into the ui and smrtflow bamboo plans, and have the bundler only arranging compiled files into their final form/directory.  That bundler work could include copying, template processing, and python module installation.  But the ui and smrtflow compilation outputs would come into the bundler as artifacts from upstream bamboo plans rather than being compiled by the bundler.

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
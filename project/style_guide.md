# PacBio Scala Coding Style Guide

Minimal style guidelines with a subset enforced by Scalastyle.

- Introduction
- Enforcing with Scalastyle
  - Run Scalastyle via SBT
  - Run Scalastyle via IntelliJ
  - Git hooks and GH enforcement
- Disabling Style Checks
- Formatting Examples

- History

## Introduction

We're following the official [Scala Style Guide](http://docs.scala-lang.org/style/)
along with Twitter's "Effective Scala" guide. The goal is to keep our
codebase readable and consistent.

A quote from Effective Scala.

> The specifics of code formatting — so long as they are practical — are
> of little consequence. By definition style cannot be inherently good
> or bad and almost everybody differs in personal preference. However 
> the consistent application of the same formatting rules will almost 
> always enhance readability. A reader already familiar with a
> particular style does not have to grasp yet another set of local
> conventions, or decipher yet another corner of the language grammar.

The current MO is if you can't easily cite the style rule in the above
noted references, we don't enforce it. It is likely a personal
preference. Feel free to file a ticket for consideration in later style
guide updates.

## Enforcing with Scalastyle

[Scalastyle](http://www.scalastyle.org/) is used to check stuff that has
an algorithm for checking. If something fails, fix it. Otherwise, err on
the side of avoiding debates about edge cases. File a ticket or make an
improved scalastyle checker for use.

### Run Scalastyle via SBT

```bash
sbt scalastyle
```

### Run Scalastyle via IntelliJ

Turn on the Scalastyle checker by selecting `Settings -> Editor -> Inspections` and "Scala style inspection.

![Enable Scalastyle in IntelliJ](https://cloud.githubusercontent.com/assets/855834/15577203/79e3ef4c-2329-11e6-8a0e-93de4f097556.png)

Afterwards, you should see code issues highlighted.

### Git hooks and GH enforcement

We currently do not have the style checks enforced by Git or GitHub. You
could add a custome git hook, but instructions are not yet present here.

## Disabling Style Checks

Scalastyle includes support for [disabling the checker via comments](http://www.scalastyle.org/configuration.html#comment_filters).
Use as needed. If the algorithm needs improvement, please file a ticket
and try to submit a patch.

## Formatting Examples

These are only intended to speed up learning typical expected formatting
and avoiding being frustrated when a style check fails and you can't figure out how to fix it.

### Class `extends` and `with` formatting and self-types

If it fits on one line, then make one line.

```scala
class Foo extends Bar with MyTrait {
...
```

If it needs more than one line, use 4-spaced params and 2-paced `extends`
or `with`.

```scala
class ImportFastaServiceType(
    dbActor: ActorRef,
    userActor: ActorRef,
    engineManagerActor: ActorRef,
    authenticator: Authenticator,
    serviceStatusHost: String,
    port: Int)
  extends JobTypeService
  with LazyLogging {
...
```

If using a [self-type](https://github.com/PacificBiosciences/smrtflow/pull/95/files#diff-b771f75642ec1d3f41b932c7600a8f7cL125) that then uses `with`.

```scala
trait ImportFastaServiceTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with UserServiceActorRefProvider
    with EngineManagerActorProvider
    with SmrtLinkConfigProvider
    with JobManagerServiceProvider =>
...
```

### Comments

See [Effective Scala's comment section](http://twitter.github.io/effectivescala/#Formatting-Comments). It overrides default scaladoc.

>Use [ScalaDoc](https://wiki.scala-lang.org/display/SW/Scaladoc) to provide API documentation. Use the following style:
> 
> /**
>  * ServiceBuilder builds services 
>  * ...
>  */
> but not the standard ScalaDoc style:
> 
> /** ServiceBuilder builds services
>  * ...
>  */

## History

See [PacificBiosciences/smrtflow#95](https://github.com/PacificBiosciences/smrtflow/pull/95) for the initial PR and discussion.
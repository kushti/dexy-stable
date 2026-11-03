# dexy-stable

This repository contains everything related to Dexy stablecoin protocol and its testnet and mainnet deployments.

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/kushti/dexy-stable)

## Contents

* [Whitepaper](paper/dexy.pdf)
* [Contracts](contracts/)
* [Specification](spec/spec.md)
* [Deployment details for DexyGold](spec/deployment-gold.md)
* [Deployment details for DexyUSD](spec/deployment-usd.md)
* [Dexy Stablecoin Improvement Proposals (UIPs)](Uips/)

## Kiosk

The test suite and deployment helpers use the [Kiosk](https://github.com/ergoplatform/kiosk)
library, which is no longer available from public repositories (the 1.0.2 release and the
ergo-core snapshots disappeared after the Sonatype OSSRH shutdown). It is therefore vendored as
an unmanaged jar in [lib/](lib/) (sbt picks up `lib/*.jar` automatically):

* `lib/kiosk_2.13.jar`, `lib/kiosk_2.13-sources.jar` — compiled library and sources for Scala 2.13
* `lib/kiosk_2.12.jar`, `lib/kiosk_2.12-sources.jar` — same for Scala 2.12

`build.sbt` puts only the jar matching the build's Scala version on the classpath.

**Upstream revision**: kiosk `master` @
[`1e7aaa35481e80dc0ea5ea1bd7de1c20ff04b7ac`](https://github.com/ergoplatform/kiosk/commit/1e7aaa35481e80dc0ea5ea1bd7de1c20ff04b7ac)
(2025-03-13, the current master HEAD), version 1.0.0.

**Local changes** on top of that revision (the first two are required by the test suite):

1. `org/ergoplatform/kiosk/ergo/package.scala` — restored `KioskBoolean` (present in kiosk
   1.0.2, removed in master).
2. `org/ergoplatform/kiosk/tx/TxUtil.scala` (+ `KioskBox` in `ergo/package.scala`) — added a
   `creationHeight: Option[Int]` passthrough so tests can set the creation height of
   transaction outputs.
3. 2.12 jars only: two source-level compatibility fixes, since kiosk master is written for
   2.13 — `HttpClientTesting.scala` uses `scala.collection.JavaConverters._` instead of
   `scala.jdk.CollectionConverters`, and `ScriptUtil.scala` replaces `env.view.mapValues(...)`
   with an explicit `map`. No behavior changes.

The diffs above are the only deviations from upstream master (items 1–2 for 2.13, items 1–3
for 2.12). Note the build also forces
`dependencyOverrides += "org.scorexfoundation" %% "scrypto" % "2.3.0"` (see `build.sbt`),
because the scrypto snapshot that sigma-state 5.0.13 references was purged as well.

## TODO

* made final contracts audit

* check tests coverage

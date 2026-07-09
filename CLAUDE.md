# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

- **Backend:** Scala 2.13.17, Play Framework 3.0.x, Pekko 1.2 (cluster + typed), Guice DI (via `scala-guice`).
- **Build:** sbt 1.11.7 (use the wrapper `./sbt`). JDK 11 (Amazon Corretto in CI).
- **Search/storage:** ElasticSearch 8.19.x via `elastic4s` 8.19. No relational DB. Default HTTP port: `9001`.
- **Frontend:** AngularJS 1.7 + Bootstrap 3, bundled with webpack 3, lives in `www/`. Node 18.16 in CI. Requires `npm install --legacy-peer-deps (--ignore-scripts for macos zsh)` (pinned legacy deps).
- **Job execution:** runs analyzers/responders as Docker containers, Kubernetes pods, or local subprocesses.

## Commands

### Build / run
- `./sbt run` — start the Cortex Play app (port 9001). First run also builds the front-end via the `FrontEnd` sbt plugin (`npm install --legacy-peer-deps && npm run build` inside `www/`).
- `./sbt compile` — backend only.
- `./sbt clean stage` — produces `target/universal/stage` runnable layout.
- `./sbt Universal/packageBin` — full distributable zip (this is what CI runs alongside tests).
- `./sbt Debian/packageBin Rpm/packageBin Docker/publishLocal` — OS packages and local Docker image. `DockerSettings` produces two images: `cortex` (slim) and `cortexWithDeps` (the `target/docker-withdeps` virtual project, used when you want bundled deps; see `build.sbt`).
- Opt-in sbt plugins: `./sbt -Dplugins=sbom,depcheck …` enables `sbt-sbom` and `sbt-dependency-check` (off by default — see `project/plugins.sbt`).

### Frontend (standalone)
- `cd www && npm install --legacy-peer-deps (--ignore-scripts for macos zsh)`
- `npm run dev` — webpack-dev-server with hot reload.
- `npm run build` — production bundle into `www/dist`, which `FrontEnd.scala` then packages into the Play assets.

### Tests
- `./sbt test` — runs the whole suite. Tests are **forked** and **non-parallel** (`Test / fork := true`, `Test / parallelExecution := false` in `project/Common.scala`).
- Single spec: `./sbt "testOnly org.thp.cortex.services.JobRunnerSrvSpec"` (Specs2 with `@RunWith(classOf[JUnitRunner])`).
- Specs2 example filter: `./sbt "testOnly *JobRunnerSrvSpec -- only \"return the original image when prefix is empty\""`.

### Formatting
- `./sbt scalafmtAll` (config in `.scalafmt.conf`, maxColumn 150, sorts imports/modifiers, rewrites unicode arrows). CI does not auto-format; run before committing.

## Architecture

### Multi-project sbt layout
`build.sbt` defines three projects:
- **`cortex`** (root, `app/`) — the Play app. Enables `PlayScala` + packaging plugins.
- **`elastic4play`** (subdir `elastic4play/`) — an in-tree library wrapping ElasticSearch as a Play-friendly data layer: `ModelDef`/`EntityDef`/`AttributeDef` DSL, `CreateSrv`/`UpdateSrv`/`FindSrv`/`AttachmentSrv`, `MigrationCtrl`, and `auth/` provider scaffolding. Cortex `dependsOn(elastic4play)`. Changes to ES models or query plumbing usually live here, not in `app/`.
- **`cortexWithDeps`** — virtual project at `target/docker-withdeps` purely to produce the `cortex-withdeps` Docker tag from the main project's mappings.

### Backend package layout (`app/org/thp/cortex`)
- `Module.scala` — Guice bindings; registered via `play.modules.enabled += org.thp.cortex.Module` in `conf/reference.conf`.
- `controllers/` — Play actions, one file per resource (Analyzer, Responder, Job, User, Organization, Stream, Misp, Auth, …). Routes wired in `conf/routes`.
- `models/` — ES-backed entities built on the elastic4play attribute DSL (`Job`, `Worker`, `Organization`, `User`, `Report`, `Artifact`, `Audit`, `WorkerConfig`, `WorkerDefinition`).
- `services/` — business logic. Most controllers are thin wrappers around a `*Srv`.
- `services/mappers/` — group/role mappers used by external auth (LDAP/AD/OAuth2 → org+role mapping).

### Worker model (analyzers + responders)
- A **Worker** is the running instance of a **WorkerDefinition** (analyzer or responder catalog entry, loaded from URLs in `analyzer.urls` / `responder.urls`). `WorkerSrv` loads definitions on startup and on demand, with `worker.updateDockerImage = true` triggering image refreshes when the catalog changes.
- A **Job** is one execution of a Worker against an artifact; it produces a **Report** and possibly child **Artifacts** (extracted IoCs). See `models/Job.scala` for the `JobStatus` enum (`Waiting`, `InProgress`, `Success`, `Failure`, `Deleted`) and the attribute schema.
- **Caching:** identical `(worker, data)` jobs within `cache.job` (default 10 min) reuse the previous report — the cache key is `cacheTag` on the Job.

### Job runner selection (`JobRunnerSrv`)
At startup, `job.runners` in config (default `[kubernetes, docker, process]`) is filtered down to actually-available runners:
- `kubernetes` requires the fabric8 client to detect a cluster (`K8sJobRunnerSrv.isAvailable`).
- `docker` requires a reachable Docker daemon (`DockerJobRunnerSrv.isAvailable`).
- `process` requires `cortexutils` Python package ≥ 2.0 to be installed (probed for `python`, `python2`, `python3`).

Runners are tried **in the configured order** for each job — first one able to run the worker wins. When editing runner logic, keep in mind the docker image name can be rewritten through `docker.imageRegistryPrefix` (see `JobRunnerSrv.applyImagePrefix` and its spec).

### Auth
`auth.provider` is a **list** evaluated in order (`local`, `ad`, `ldap`, `oauth2`, `key`). `CortexAuthSrv` composes them; multi-valued is the supported way to migrate users between providers. API-key auth (`KeyAuthSrv`) is always available alongside whatever interactive providers are configured.

### Configuration
- `conf/reference.conf` ships defaults; operators override via `conf/application.conf` (template: `conf/application.sample`).
- Job runner, cache TTLs, ES connection, auth providers and per-provider config, and analyzer/responder catalogs all live in HOCON.

## Conventions specific to this codebase

- **Models are not plain case classes** — they extend `ModelDef[…]` + a `*Attributes` trait from elastic4play. Adding a field means editing both the trait and (often) a migration. Look at `models/Job.scala` for the canonical pattern.
- **No DB migrations file** — schema lives in code; the `MigrationCtrl` endpoint (`POST /api/maintenance/migrate`) runs version-aware migrations registered through elastic4play.
- **`organization` is the tenant boundary.** Almost every model carries an `organization` attribute and `*Srv` queries scope by it via `AuthContext`. Don't add cross-organization queries without explicit ACL handling.
- **Routes file is the source of truth** for the public API surface (`conf/routes`) — there's no annotation-based routing.
- **The front-end is legacy AngularJS 1.x** and is *not* under active framework upgrades; keep changes minimal and idiomatic to the existing module structure (`www/src/app/{components,pages,core}`).

## Repository docs / refs

- `README.md` — high-level product description and links to external docs.
- `CHANGELOG.md` — release-by-release feature/fix list (DL-xxxx ticket prefixes match the team's Jira).
- External docs site: <https://docs.strangebee.com/cortex/> (generated from `docs/`, built via `.github/workflows/build.docs.yaml`).
- This repo is mirrored to the public OSS repo at <https://github.com/TheHive-Project/Cortex>. The release process is **manual** — pushes to the mirror and version bumps are not automated.
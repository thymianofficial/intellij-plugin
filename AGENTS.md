# AGENTS.md — thymian IntelliJ plugin

Operational ground rules for AI agents working in this repository. This is not a
contribution guide.

## What this is

A JetBrains IDE plugin ([Marketplace `33090-thymian`](https://plugins.jetbrains.com/plugin/33090-thymian))
that talks to the thymian CLI over the `@thymian/plugin-websocket-proxy` websocket API.
The production runner (`src/main/kotlin/dev/thymian/client/cli/ThymianCLILocalRunner.kt`)
spawns `npx --yes thymian@latest serve …` — or, when `ThymianSettings.thymianCliPath` is
configured, the configured CLI entry directly
(`<entry> serve --rule-severity=hint -o @thymian/plugin-websocket-proxy.port=<port>`; the
path is split on a `ThymianSettings.RUN_FILE_OPTIONS` suffix into working directory +
relative command) — waits for the serve banner, and quits the CLI by writing `q` to its
stdin. The Kotlin protocol mirror is `cli/Message.kt`; the handshake
(register → register-ack → ready) is implemented in `cli/ThymianCLIAdapter.kt`.

## Build / test / run

Kotlin, Gradle Kotlin DSL, JVM toolchain 21. Always go through the wrapper:

```
./gradlew build | check | test | verifyPlugin | runIde
```

A local `qodanaScan` task exists (Gradle Qodana plugin). CI (`.github/workflows/build.yml`)
runs build, test (`check`), Qodana inspection, plugin verification, and the real-CLI e2e
test on PRs.

## Conventions that bite agents

- **Dependency versions live in `gradle/libs.versions.toml`** (version catalog);
  **platform coordinates** (`platformType`, `platformVersion`, `pluginSinceBuild`, bundled
  plugins/modules) live in **`gradle.properties`** — never hardcode either in build scripts.
- The README `<!-- Plugin description -->` … `<!-- Plugin description end -->` markers are
  extracted into the plugin manifest at build time; the build **fails** without them.
- `kotlinx-coroutines-core` is globally excluded from dependencies (the IDE platform
  provides it) — do not re-add it.
- Package root is `dev.thymian.client`.
- Tests are JUnit 4 + the IntelliJ Platform test framework: unit/integration tests under
  `src/test/kotlin` (run via `check`); the real-CLI e2e under `src/e2eTest/kotlin` (its own
  `e2eTest` task, deliberately excluded from `check` — including Kover's on-check reports).
- Commits: unscoped conventional prefixes (`feat:`, `fix:`, `chore:`, `test:`), single-line,
  lowercase, imperative. No commitlint; only Dependabot uses scopes.

## Real-CLI e2e test (`src/e2eTest/kotlin`)

A Kotlin `BasePlatformTestCase` that drives the plugin's **production CLI stack**
(`SequentialThymianCLISessionManager` → `ThymianCLILocalRunner` → `ThymianCLIAdapter`)
against a **freshly built** thymian CLI over a real websocket — one `core.workflow.lint`
round-trip. There is no vendored protocol copy and no Node toolchain in this repo: the
plugin's own `cli/Message.kt`, `ThymianCLIAdapter.kt`, and `ThymianCLILocalRunner.kt` ARE
the contract under test.

Invocation (the CLI path is required; there is no npx fallback):

```
./gradlew e2eTest -PthymianCliPath=<thymian checkout>/packages/thymian/bin/dev.js
```

`THYMIAN_CLI_PATH` (environment) works as an alternative to `-PthymianCliPath`. A missing
or invalid path fails fast — the Gradle task errors before the test JVM forks, and the test
re-validates the file in `setUp`.

Local setup: build the sibling `thymian/` checkout first (`npm ci`, then
`npx nx run-many -t build --exclude astro --no-tui` — thymian's own CLI-sufficient build).
`bin/dev.js` is the checkout entry point; `bin/run.js` only works installed under
`node_modules`. In CI the `e2e` job builds `thymianofficial/thymian@main` fresh on every PR.

**What a red e2e means:** the plugin's protocol client and thymian@main have drifted —
assertions target the mechanism (session settles, a real `Report` arrives and is grouped, no
adapter/action error, clean CLI shutdown), not rule outcomes, so rule-result changes on
thymian@main do not break it. Treat a red run as a real cross-repo finding (versioning
policy: `thymian-internal#649`), not as a flake to retry. The cross-repo e2e loop in
`thymianofficial/thymian-internal` (story 402.4) drives this Gradle target.

## Process

Feature planning and the BMAD story workflow live in `thymianofficial/thymian-internal`;
stories targeting this repo carry the `repo:intellij-plugin` label there.

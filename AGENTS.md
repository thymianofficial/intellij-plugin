# AGENTS.md — thymian IntelliJ plugin

Operational ground rules for AI agents working in this repository. This is not a
contribution guide.

## What this is

A JetBrains IDE plugin ([Marketplace `33090-thymian`](https://plugins.jetbrains.com/plugin/33090-thymian))
that talks to the thymian CLI over the `@thymian/plugin-websocket-proxy` websocket API.
The production runner (`src/main/kotlin/dev/thymian/client/cli/ThymianCLILocalRunner.kt`)
spawns `npx --yes thymian@latest serve …`, waits for the serve banner, and quits the CLI by
writing `q` to its stdin. The Kotlin protocol mirror is `cli/Message.kt`; the handshake
(register → register-ack → ready) is implemented in `cli/ThymianCLIAdapter.kt`.

## Build / test / run

Kotlin, Gradle Kotlin DSL, JVM toolchain 21. Always go through the wrapper:

```
./gradlew build | check | test | verifyPlugin | runIde
```

A local `qodanaScan` task exists (Gradle Qodana plugin). CI (`.github/workflows/build.yml`)
runs build, test, Qodana inspection, plugin verification, and the thymian e2e suite on PRs.

## Conventions that bite agents

- **Dependency versions live in `gradle/libs.versions.toml`** (version catalog);
  **platform coordinates** (`platformType`, `platformVersion`, `pluginSinceBuild`, bundled
  plugins/modules) live in **`gradle.properties`** — never hardcode either in build scripts.
- The README `<!-- Plugin description -->` … `<!-- Plugin description end -->` markers are
  extracted into the plugin manifest at build time; the build **fails** without them.
- `kotlinx-coroutines-core` is globally excluded from dependencies (the IDE platform
  provides it) — do not re-add it.
- Package root is `dev.thymian.client`.
- Tests are JUnit 4 + the IntelliJ Platform test framework, under `src/test/kotlin`.
- Commits: unscoped conventional prefixes (`feat:`, `fix:`, `chore:`, `test:`), single-line,
  lowercase, imperative. No commitlint; only Dependabot uses scopes.

## Thymian e2e harness (`e2e-tests/`)

A Node/vitest smoke suite that freezes the websocket protocol contract against the
**published** thymian CLI:

```
npm ci
npm run e2e        # from the repo root, no env vars needed
```

Environment variables (all optional):

| Variable | Effect |
|----------|--------|
| `THYMIAN_E2E_MODE` | `npx` (default), `global` (bare `thymian` from PATH), or `local` (a built checkout via `THYMIAN_E2E_CLI`) |
| `THYMIAN_E2E_VERSION` | overrides the committed exact pin (`DEFAULT_THYMIAN_VERSION` in `e2e-tests/src/helpers.ts`); `npx` mode only |
| `THYMIAN_E2E_REGISTRY` | mapped to `npm_config_registry` in the spawned CLI's environment; unset ⇒ the harness adds no registry override (ambient npm config still applies) |
| `THYMIAN_E2E_CLI` | required in `local` mode: path to a built thymian entry (e.g. `<thymian>/packages/thymian/bin/run.js`), run with the current node binary — how the cross-repo drift loop tests unreleased thymian source |

**Pin policy:** the default version is an **exact** string (never a range) — lockstep
versioning means one string covers all `@thymian/*`. Bumping the pin is a deliberate PR:
edit `DEFAULT_THYMIAN_VERSION`, re-vendor the protocol snapshot (below), and let CI's e2e
job exercise the new version.

**Vendored protocol snapshot:** `e2e-tests/src/vendor/{messages.ts,reference-client.ts}`
are copied from `thymianofficial/thymian` at the pinned tag (provenance headers list the
exact source paths, tag, commit SHA, and every adaptation). No client SDK is published and
the handshake has no protocol version field — the frozen snapshot IS the versioning
mechanism. To re-vendor: `git show <tag>:<path>` in a thymian checkout, re-apply the
adaptations listed in each header, update the headers. Never add fields to the register
message — the server schemas are `additionalProperties: false` and will close the socket.

If the smoke test breaks on a pin bump, the production files that assume the same contract
(`ThymianCLILocalRunner.kt`, `ThymianCLIAdapter.kt`, `cli/Message.kt`) likely need
attention too.

## Process

Feature planning and the BMAD story workflow live in `thymianofficial/thymian-internal`;
stories targeting this repo carry the `repo:intellij-plugin` label there.

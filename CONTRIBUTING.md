# Contributing to the Thymian IntelliJ plugin

Thanks for contributing! This guide covers the conventions for working in this
repository. Operational ground rules for AI agents live in [AGENTS.md](AGENTS.md);
this document is the human contributor guide.

## Build, test, run

Everything goes through the Gradle wrapper (`./gradlew`); the JVM toolchain is 21.

- `./gradlew buildPlugin` — build the distributable plugin.
- `./gradlew check` — unit/integration tests plus Kover coverage.
- `./gradlew verifyPlugin` — IntelliJ Plugin Verifier against the recommended IDE set.
- `./gradlew runIde` — launch a sandbox IDE with the plugin installed.
- `./gradlew qodanaScan` — local Qodana static analysis (CI runs it on every PR).

Gradle's configuration cache and build cache are enabled (`gradle.properties`) — keep
any build-script change configuration-cache compatible.

## Tests

- Unit/integration tests are JUnit 4 + the IntelliJ Platform test framework, under
  `src/test/kotlin`, and run as part of `check`.
- The real-CLI end-to-end test lives in its own source set, `src/e2eTest/kotlin`, and is
  deliberately excluded from `check`: it needs a locally built thymian CLI and runs via
  `./gradlew e2eTest -PthymianCliPath=…`. See the "Real-CLI e2e test" section of
  [AGENTS.md](AGENTS.md) for the details and local setup.

## Conventions

- **Dependency versions** live only in the version catalog, `gradle/libs.versions.toml`;
  **platform coordinates** (`platformType`, `platformVersion`, `pluginSinceBuild`,
  bundled plugins/modules) live only in `gradle.properties`. Never hardcode either in
  `build.gradle.kts`.
- The README `<!-- Plugin description -->` … `<!-- Plugin description end -->` markers
  are extracted into the plugin manifest at build time; the build **fails** without
  them — keep them intact.
- `kotlinx-coroutines-core` is globally excluded from dependencies (the IDE platform
  provides it) — do not add it back.
- The package root is `dev.thymian.client`.

## Commits

Unscoped conventional-commit prefixes (`feat:`, `fix:`, `chore:`, `test:`), single-line,
lowercase, imperative — e.g. `fix: handle cli quit timeout`. There is no
commitlint/husky enforcement; match the existing history. (Thymian's scoped commit
rules do not apply in this repo — only Dependabot uses scopes here.)

## Releases

`CHANGELOG.md` is managed by the Gradle Changelog Plugin: put notable changes under
**Unreleased**. CI drafts a GitHub Release from the unreleased notes; publishing that
release runs `publishPlugin` (JetBrains Marketplace) and `patchChangelog` commits the
release notes back.

## Planning and stories

Feature planning and the BMAD story workflow live in the
`thymianofficial/thymian-internal` repository — stories targeting this repo carry the
`repo:intellij-plugin` label there. Open issues there rather than in this repo.

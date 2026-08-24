# Contributing to the Thymian IntelliJ plugin

Thanks for contributing! This guide is the canonical home of the conventions for
working in this repository. Operational ground rules for AI agents live in
[AGENTS.md](AGENTS.md); this document is the human contributor guide.

## Build, test, run

Everything goes through the Gradle wrapper (`./gradlew`); the JVM toolchain is 21.

- `./gradlew buildPlugin` — build the distributable plugin.
- `./gradlew test` — run the unit/integration tests.
- `./gradlew check` — tests plus Kover coverage verification.
- `./gradlew verifyPlugin` — IntelliJ Plugin Verifier against the recommended IDE set.
- `./gradlew runIde` — launch a sandbox IDE with the plugin installed.
- `./gradlew qodanaScan` — Qodana static analysis locally; CI runs Qodana on every PR
  via `JetBrains/qodana-action`.

Ready-made IDE run configurations for the common tasks are committed under `.run/`.

Gradle's configuration cache and build cache are enabled (`gradle.properties`) — keep
any build-script change configuration-cache compatible.

## Tests

- Unit/integration tests are JUnit 4 + the IntelliJ Platform test framework, under
  `src/test/kotlin`, and run as part of `check`; CI uploads the Kover coverage report
  to Codecov.
- The real-CLI end-to-end test lives in its own source set, `src/e2eTest/kotlin`, and is
  deliberately excluded from `check`: it needs a locally built thymian CLI and runs via
  `./gradlew e2eTest -PthymianCliPath=…`. See the "Real-CLI e2e test" section of
  [AGENTS.md](AGENTS.md) for the details and local setup.
- UI tests run in their own, manually triggered workflow (`Run UI Tests` under GitHub
  Actions).

## Build conventions

- **Dependency versions** live only in the version catalog, `gradle/libs.versions.toml`;
  **platform coordinates** (`platformType`, `platformVersion`, `pluginSinceBuild`,
  bundled plugins/modules) live only in `gradle.properties`. Never hardcode either in
  `build.gradle.kts`.
- The README `<!-- Plugin description -->` … `<!-- Plugin description end -->` markers
  are extracted into the plugin manifest at build time; the build **fails** without
  them — keep them intact.
- `kotlinx-coroutines-core` is globally excluded from dependencies (the IDE platform
  provides it) — do not add it back.
- The Kotlin stdlib is deliberately **not** bundled
  (`kotlin.stdlib.default.dependency=false` in `gradle.properties`) — the platform
  provides that too.

## Code conventions

- The package root is `dev.thymian.client`; keep new code in the matching subpackage
  (`cli`, `endpoints`, `report`, `run`, `settings`).
- **No user-facing string literals in code** — add keys to
  `src/main/resources/messages/ThymianBundle.properties` and read them via
  `ThymianBundle.message("key")`.
- Production classes are `internal`; test classes must be public.
- Services are application-level: register them in `META-INF/plugin.xml`
  (`<applicationService>`, interface + implementation split) and retrieve them through
  a `getInstance()` companion.
- Async code bridges coroutines to `CompletableFuture` (`.asCompletableFuture()`) at
  public API boundaries; inject `CoroutineScope` instead of creating your own threads
  or dispatchers.
- CLI message protocol (`cli/Message.kt`): every inbound/response type carries
  `@JsonIgnoreUnknownKeys` (forward compatibility with the CLI), constant discriminator
  fields use `@EncodeDefault`, and a new CLI action/result gets wired into the `when`
  in `Receiving.ActionResultMessageWrapper.toTypedMessage()`.

## Commits

Unscoped conventional-commit prefixes (e.g. `feat:`, `fix:`, `chore:`, `test:`,
`docs:`, `refactor:`), single-line, lowercase, imperative — e.g. `fix: handle cli quit
timeout`. There is no commitlint/husky enforcement; match the existing history.
(Thymian's scoped commit rules do not apply in this repo — only Dependabot uses scopes
here.)

## Releases

`CHANGELOG.md` is managed by the Gradle Changelog Plugin: put notable changes under
**Unreleased**. CI drafts a GitHub Release from the unreleased notes once every check
on `main` is green — including the cross-repo e2e against a freshly built thymian CLI,
so upstream CLI drift blocks release drafting by design. Publishing that release runs
`publishPlugin` (JetBrains Marketplace) and `patchChangelog`, and CI opens a follow-up
PR that moves the released notes out of **Unreleased**.

## Planning and stories

Feature planning and the story workflow live in a private planning repo, not in this
repo's issue tracker.

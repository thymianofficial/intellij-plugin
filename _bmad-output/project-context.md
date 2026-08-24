---
project_name: 'Thymian Intellij plugin'
user_name: 'Andreas'
date: '2026-07-16'
sections_completed: ['technology_stack', 'build_dependencies', 'platform_patterns', 'async_concurrency', 'message_protocol', 'localization', 'testing', 'structure_release']
status: 'complete'
rule_count: 22
optimized_for_llm: true
---

# Project Context for AI Agents

_This file contains critical rules and patterns that AI agents must follow when implementing code in this project. Focus on unobvious details that agents might otherwise miss._

---

## Technology Stack & Versions

**Never hardcode or assume versions. Read them from source-of-truth files:**

- **`gradle/libs.versions.toml`** — the version catalog: all library and Gradle-plugin versions (Kotlin, Ktor client, kotlinx-serialization, JUnit, Kover, Qodana, Changelog, IntelliJ Platform Gradle Plugin).
- **`gradle.properties`** — IntelliJ Platform target (`platformType`, `platformVersion`, `pluginSinceBuild`), `pluginVersion`, `gradleVersion`, bundled plugins/modules, and build flags.
- **`build.gradle.kts`** — JVM language level (`kotlin { jvmToolchain(...) }`) and how the above are wired in.

**Rule:** Declare dependency versions only in `libs.versions.toml` and reference them via `libs.` accessors. Never inline a version string in `build.gradle.kts`.

## Critical Implementation Rules

### Build & Dependency Constraints

- **`kotlinx-coroutines-core` is globally excluded** (`configurations.all { exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core") }`). Use the IntelliJ Platform's **bundled** coroutines — never add coroutines-core as a dependency.
- **Kotlin stdlib is not bundled** (`kotlin.stdlib.default.dependency=false`); it's provided by the platform.
- **Gradle configuration cache + build cache are ON.** All build logic must stay config-cache compatible (capture `project.*` into local vals, no `Project` access at execution time — see the `val changelog = project.changelog` pattern in `build.gradle.kts`).

### IntelliJ Platform Patterns

- **Production classes are `internal`;** test classes must be public.
- **Services are application-level:** register in `META-INF/plugin.xml` (`<applicationService>`, interface + impl split), retrieve via `ApplicationManager.getApplication().getService(X::class.java)` behind a `getInstance()` companion.
- Register actions/extensions in `plugin.xml`. (Note: `ThymianRunConfigurationType` is currently commented out there.)
- Prefer platform utilities (`SystemInfo`, `com.intellij.util.io.awaitExit`, …) over raw Java equivalents. OS-specific executables must branch on `SystemInfo.isWindows`.

### Async & Concurrency

- Public API boundaries return `java.util.concurrent.CompletableFuture`, bridged from coroutines via `.asCompletableFuture()`.
- **Inject `CoroutineScope`** — don't create your own threads/dispatchers; use platform `Dispatchers`.

### CLI Message Protocol (`cli/Message.kt`)

- Sealed hierarchies use `@JsonClassDiscriminator` (`"name"` for actions, `"type"` for receiving).
- **All inbound/response types carry `@JsonIgnoreUnknownKeys`** for forward-compat with the CLI — keep it on any new inbound type.
- Constant discriminator fields use `@EncodeDefault` (+ `@OptIn(ExperimentalSerializationApi::class)`); message ids use `Uuid.random().toString()` (+ `@OptIn(ExperimentalUuidApi::class)`).
- When adding a CLI action/result, wire it into the `when` in `Receiving.ActionResultMessageWrapper.toTypedMessage()`.

### Localization

- **No user-facing string literals in code.** Add keys to `src/main/resources/messages/ThymianBundle.properties` and read via `ThymianBundle.message("key")`.

### Testing

- JUnit 4 + `BasePlatformTestCase`; test methods use backtick names.
- Swap services/extensions with `application.replaceService(...)` and `ExtensionTestUtil.maskExtensions(...)`, scoped to `testRootDisposable`.
- One integration test is intentionally disabled pending JetBrains **IDEA-385865** — don't "fix" it blindly.
- Verify with `./gradlew test verifyPlugin`; coverage via Kover, static analysis via Qodana.

### Project Structure & Release

- Root package `dev.thymian.client` with `cli`, `endpoints`, `run`, `settings` subpackages — keep new code in the matching one.
- `CHANGELOG.md` follows the Changelog-plugin format; `publishPlugin` depends on `patchChangelog`. Put notable changes under **Unreleased**.
- The README plugin description lives between `<!-- Plugin description -->` markers; the build extracts it — keep the markers intact.

### Issue Tracking

- Tickets are managed in a private internal planning repo, **not** in this plugin's public repo.

---

## Usage Guidelines

**For AI Agents:**

- Read this file before implementing any code.
- Follow all rules exactly; when in doubt, prefer the more restrictive option.
- Resolve versions from `gradle/libs.versions.toml` and `gradle.properties` at the time of the task — do not rely on any version stated in conversation.
- Update this file if new durable patterns emerge.

**For Humans:**

- Keep this file lean and focused on what agents would otherwise miss.
- Update when the dependency/build constraints or platform patterns change.
- Remove rules that become obvious or obsolete over time.

Last Updated: 2026-07-16

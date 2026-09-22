<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-plugin Changelog

## [Unreleased]

### Fixed

- Plugin failing `verifyPlugin` / breaking at runtime on 2026.3 (build 263): replaced the
  removed experimental `com.intellij.collaboration.async.cancelAndJoinSilently` platform API
  with a local helper.
- Refactored ThymianBundle to current suggestion in IntelliJ docs, because the last implementation failed
  `verifyPlugin`.

## [0.0.1]

### Added

- Added options for running Thymian checks in endpoints view.
- Initial scaffold created
  from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

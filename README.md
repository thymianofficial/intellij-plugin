# Thymian IntelliJ Plugin

![Build](https://github.com/thymianofficial/intellij-plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/33090-thymian.svg)](https://plugins.jetbrains.com/plugin/33090-thymian)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/33090-thymian.svg)](https://plugins.jetbrains.com/plugin/33090-thymian)

<!-- Plugin description -->
Official Thymian IntelliJ IDEA plugin.

This plugin connects your JetBrains IDE to your local Thymian CLI.

Thymian checks your API or endpoints from API controllers against the HTTP specification or own HTTP API tests and
provides you with a detailed report.

Check out the [Thymian CLI website](https://thymian.dev) for more information.

## Requirements

Node.js® and npm need to be installed on your machine. Additionally, you can have either the plugin use Thymian via
`npx --yes thymian@latest` or you install the Thymian CLI on your local machine and set the path to the `run.js` in the
settings.

## How to use

The Thymian check is available in the endpoints list as a tab and as an action in the context menu. Either triggers the
check on the selected endpoint.

## Notes

At the moment, it's not possible to check a single endpoint for API specification files like an OpenAPI specification
yaml or json file. Triggering the check on a single endpoint will check the whole API specification.

<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "
  intellij-plugin"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking
  the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from
  JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/thymianofficial/intellij-plugin/releases/latest) and install it
  manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template

[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation

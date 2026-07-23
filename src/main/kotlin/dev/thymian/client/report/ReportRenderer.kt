package dev.thymian.client.report

import dev.thymian.client.cli.Execution
import dev.thymian.client.cli.ExecutionStatus
import dev.thymian.client.cli.Finding
import dev.thymian.client.cli.RuleDescriptor

/**
 * Kotlin port of the Thymian CLI's lint/analyze rendering
 * (`packages/common-cli/src/render/{status,findings,lint-analyze-executions,create-execution-renderer}.ts`),
 * scoped to symbols/layout only — no ANSI colors, since the Run tool window conveys
 * pass/fail state through the [com.intellij.execution.testframework.sm.runner.SMTestProxy]
 * node itself.
 */

private const val ERROR_SYMBOL = "✖"
private const val WARN_SYMBOL = "⚠"
private const val HINT_SYMBOL = "✎"
private const val INFO_SYMBOL = "ℹ"
private const val SUCCESS_SYMBOL = "✓"
private const val SKIPPED_SYMBOL = "⏭"

private const val SINGLE_INDENTATION = "  "
private fun indent(times: Int): String = SINGLE_INDENTATION.repeat(times)

/** One indent unit = 2 spaces; mirrors `createExecutionsRenderer`'s levels for lint/analyze. */
private const val STATUS_LEVEL = 2
private const val RULE_ID_LEVEL = 7
private const val FINDING_BASE_LEVEL = 9

/** Builds `ruleId -> RuleDescriptor` for a run, mirroring `buildRuleIndex`. */
internal fun buildRuleIndex(rules: List<RuleDescriptor>?): Map<String, RuleDescriptor> =
    rules?.associateBy { it.id } ?: emptyMap()

/**
 * Resolves the severity of a `failed` execution (status `severity` override, else the rule's
 * configured severity, else `"error"`); returns `null` for non-`failed` executions. Mirrors
 * `resolveExecutionSeverity`.
 */
internal fun resolveExecutionSeverity(execution: Execution, ruleIndex: Map<String, RuleDescriptor>): String? {
    val status = execution.status
    if (status.kind != "failed") {
        return null
    }

    status.severity?.let { return it }

    execution.ruleId?.let { ruleId -> ruleIndex[ruleId]?.severity?.let { return it } }

    return "error"
}

private fun severitySymbol(severity: String): String = when (severity) {
    "error" -> ERROR_SYMBOL
    "warn" -> WARN_SYMBOL
    "hint" -> HINT_SYMBOL
    "info" -> INFO_SYMBOL
    else -> ERROR_SYMBOL
}

private fun formatDuration(durationMilliseconds: Double?): String =
    durationMilliseconds?.let { " (%.2fms)".format(it) }.orEmpty()

/** Mirrors `renderStatus`: one line describing an execution's outcome. */
internal fun renderStatus(status: ExecutionStatus, severity: String?, fallbackReason: String?): String {
    return when (status.kind) {
        "passed" -> "$SUCCESS_SYMBOL passed${formatDuration(status.durationMilliseconds)}"
        "failed" -> {
            val resolved = severity ?: "error"
            val reasonText = status.reason ?: fallbackReason
            val reason = if (!reasonText.isNullOrEmpty()) ": $reasonText" else ""
            "${severitySymbol(resolved)} $resolved$reason${formatDuration(status.durationMilliseconds)}"
        }

        "skipped" -> "$SKIPPED_SYMBOL  skipped${status.reason?.let { ": $it" } ?: ""}"
        else -> status.kind
    }
}

/** Mirrors `renderFindings`/`renderFinding` for lint/analyze (rule-violation titles dropped). */
internal fun renderFindings(findings: List<Finding>?, indentationLevel: Int): List<String> =
    (findings ?: emptyList()).flatMap { renderFinding(it, indentationLevel) }

private fun renderFinding(finding: Finding, indentationLevel: Int): List<String> = when (finding.kind) {
    "informational" -> listOf(indent(indentationLevel) + "$INFO_SYMBOL ${finding.title}")
    "assertion-success" -> listOf(indent(indentationLevel) + "$SUCCESS_SYMBOL ${finding.title}")
    "assertion-failure" -> {
        val lines = mutableListOf(indent(indentationLevel) + "$ERROR_SYMBOL ${finding.title}")
        val expected = finding.expected
        val actual = finding.actual
        if (expected != null && actual != null) {
            lines += indent(indentationLevel + 2) + "expected: $expected"
            lines += indent(indentationLevel + 2) + "actual: $actual"
        }
        lines
    }
    // "rule-violation" and unknown/superseded kinds are intentionally not rendered for lint/analyze.
    else -> emptyList()
}

/**
 * One execution paired with the rule index of the [dev.thymian.client.cli.ToolRun] it came
 * from — a location can in principle be shared by executions from different runs, each with
 * its own rule descriptors.
 */
internal data class ExecutionWithRuleIndex(val execution: Execution, val ruleIndex: Map<String, RuleDescriptor>)

/** error → warn → hint → info → unclassified (e.g. `skipped`, which has no severity). */
private fun severityRank(severity: String?): Int = when (severity) {
    "error" -> 0
    "warn" -> 1
    "hint" -> 2
    "info" -> 3
    else -> 4
}

/**
 * Renders the detail block for one endpoint's executions: a status + optional rule-id line
 * per execution, followed by its findings — mirroring `createExecutionsRenderer` +
 * `renderLintAndAnalyzeExecution` at the per-execution level (the location header itself is
 * rendered separately as the locationProxy's title / first `addStdOutput` line). Executions
 * are sorted by severity (errors first, then warns, hints, infos) so the most severe results
 * are easiest to spot; `sortedBy` is stable, so same-severity executions keep their relative
 * order. Returns the rendered lines and whether any execution in the group failed.
 */
internal fun renderLocationExecutions(executions: List<ExecutionWithRuleIndex>): Pair<List<String>, Boolean> {
    val lines = mutableListOf<String>()
    var hasFailure = false

    val sortedExecutions = executions.sortedBy { (execution, ruleIndex) ->
        severityRank(resolveExecutionSeverity(execution, ruleIndex))
    }

    for ((execution, ruleIndex) in sortedExecutions) {
        if (execution.status.kind == "passed") {
            continue
        }
        if (execution.status.kind == "failed") {
            hasFailure = true
        }

        val rule = execution.ruleId?.let { ruleIndex[it] }
        val severity = resolveExecutionSeverity(execution, ruleIndex) ?: "error"
        val fallbackReason = rule?.summary?.text ?: rule?.description?.text ?: rule?.name

        lines += indent(STATUS_LEVEL) + renderStatus(execution.status, severity, fallbackReason)

        if (rule != null) {
            lines += indent(RULE_ID_LEVEL) + "› ${rule.id}"
        }

        lines += renderFindings(execution.findings, FINDING_BASE_LEVEL)
        lines += ""
    }

    return lines to hasFailure
}

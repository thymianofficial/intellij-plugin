package dev.thymian.client.report

import dev.thymian.client.cli.Execution
import dev.thymian.client.cli.ExecutionStatus
import dev.thymian.client.cli.Finding
import dev.thymian.client.cli.FindingMessage
import dev.thymian.client.cli.RuleDescriptor
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors `packages/common-cli/test/cli-report-renderer.test.ts` for the pieces ported to
 * this plugin (`status.ts`, `findings.ts`, `finding-render.ts`), scoped to symbols/layout only
 * (no ANSI colors — see `ReportRenderer.kt`).
 */
class ReportRendererTest {

    /** One indentation unit is 2 spaces, matching `ReportRenderer`'s private `indent()`. */
    private fun ind(times: Int): String = "  ".repeat(times)

    private fun execution(
        kind: String = "lint",
        ruleId: String? = null,
        status: ExecutionStatus,
        findings: List<Finding>? = null,
    ) = Execution(kind = kind, ruleId = ruleId, status = status, findings = findings)

    @Test
    fun `resolveExecutionSeverity uses the status override when present`() {
        val status = ExecutionStatus(kind = "failed", severity = "warn")
        val execution = execution(ruleId = "rule-1", status = status)
        val ruleIndex = mapOf("rule-1" to RuleDescriptor(id = "rule-1", severity = "error"))

        assertEquals("warn", resolveExecutionSeverity(execution, ruleIndex))
    }

    @Test
    fun `resolveExecutionSeverity falls back to the rule severity`() {
        val status = ExecutionStatus(kind = "failed")
        val execution = execution(ruleId = "rule-1", status = status)
        val ruleIndex = mapOf("rule-1" to RuleDescriptor(id = "rule-1", severity = "hint"))

        assertEquals("hint", resolveExecutionSeverity(execution, ruleIndex))
    }

    @Test
    fun `resolveExecutionSeverity defaults to error when nothing resolves it`() {
        val execution = execution(status = ExecutionStatus(kind = "failed"))

        assertEquals("error", resolveExecutionSeverity(execution, emptyMap()))
    }

    @Test
    fun `resolveExecutionSeverity is null for non-failed statuses`() {
        assertEquals(null, resolveExecutionSeverity(execution(status = ExecutionStatus(kind = "passed")), emptyMap()))
        assertEquals(null, resolveExecutionSeverity(execution(status = ExecutionStatus(kind = "skipped")), emptyMap()))
    }

    @Test
    fun `renderStatus renders a passing execution`() {
        assertEquals("✓ passed", renderStatus(ExecutionStatus(kind = "passed"), null, null))
    }

    @Test
    fun `renderStatus renders a passing execution with duration`() {
        assertEquals(
            "✓ passed (12.34ms)",
            renderStatus(ExecutionStatus(kind = "passed", durationMilliseconds = 12.34), null, null),
        )
    }

    @Test
    fun `renderStatus renders a failed execution with reason`() {
        assertEquals("✖ error: broken", renderStatus(ExecutionStatus(kind = "failed", reason = "broken"), "error", null))
    }

    @Test
    fun `renderStatus falls back to the rule label when no reason is set`() {
        assertEquals(
            "⚠ warn: Example rule",
            renderStatus(ExecutionStatus(kind = "failed"), "warn", "Example rule"),
        )
    }

    @Test
    fun `renderStatus renders bare severity when there is no reason or fallback`() {
        assertEquals("✖ error", renderStatus(ExecutionStatus(kind = "failed"), "error", null))
    }

    @Test
    fun `renderStatus renders a skipped execution with reason`() {
        assertEquals(
            "⏭  skipped: not applicable",
            renderStatus(ExecutionStatus(kind = "skipped", reason = "not applicable"), null, null),
        )
    }

    @Test
    fun `renderStatus renders a skipped execution without reason`() {
        assertEquals("⏭  skipped", renderStatus(ExecutionStatus(kind = "skipped"), null, null))
    }

    @Test
    fun `renderFindings renders informational, success and failure findings`() {
        val findings = listOf(
            Finding(id = "1", kind = "informational", title = "schema validation context"),
            Finding(id = "2", kind = "assertion-success", title = "status ok"),
            Finding(id = "3", kind = "assertion-failure", title = "path parameter must be integer"),
        )

        val lines = renderFindings(findings, 9)

        assertEquals(
            listOf(
                "${ind(9)}ℹ schema validation context",
                "${ind(9)}✓ status ok",
                "${ind(9)}✖ path parameter must be integer",
            ),
            lines,
        )
    }

    @Test
    fun `renderFindings adds expected actual lines only when both are present`() {
        val withBoth = Finding(
            id = "1",
            kind = "assertion-failure",
            title = "expected status",
            expected = JsonPrimitive(200),
            actual = JsonPrimitive(201),
        )
        val withOnlyActual = Finding(
            id = "2",
            kind = "assertion-failure",
            title = "expected content type",
            actual = JsonPrimitive("text/plain"),
        )

        assertEquals(
            listOf(
                "${ind(2)}✖ expected status",
                "${ind(4)}expected: 200",
                "${ind(4)}actual: 201",
            ),
            renderFindings(listOf(withBoth), 2),
        )
        assertEquals(listOf("${ind(2)}✖ expected content type"), renderFindings(listOf(withOnlyActual), 2))
    }

    @Test
    fun `renderFindings drops rule-violation and unknown finding kinds`() {
        val findings = listOf(
            Finding(id = "1", kind = "rule-violation", title = "dropped"),
            Finding(id = "2", kind = "rule-failure", title = "also dropped"),
        )

        assertTrue(renderFindings(findings, 0).isEmpty())
    }

    @Test
    fun `renderLocationExecutions skips passing executions but reports no failure`() {
        val executions = listOf(
            ExecutionWithRuleIndex(execution(status = ExecutionStatus(kind = "passed")), emptyMap()),
        )

        val (lines, hasFailure) = renderLocationExecutions(executions)

        assertTrue(lines.isEmpty())
        assertFalse(hasFailure)
    }

    @Test
    fun `renderLocationExecutions renders failed executions with rule id and findings`() {
        val ruleIndex = mapOf(
            "content-type-charset" to RuleDescriptor(id = "content-type-charset", summary = FindingMessage(text = "Charset check")),
        )
        val executions = listOf(
            ExecutionWithRuleIndex(
                execution(
                    ruleId = "content-type-charset",
                    status = ExecutionStatus(kind = "failed", severity = "warn", reason = "msg"),
                    findings = listOf(Finding(id = "1", kind = "informational", title = "detail")),
                ),
                ruleIndex,
            ),
        )

        val (lines, hasFailure) = renderLocationExecutions(executions)

        assertTrue(hasFailure)
        assertEquals(
            listOf(
                "${ind(2)}⚠ warn: msg",
                "${ind(7)}› content-type-charset",
                "${ind(9)}ℹ detail",
                "",
            ),
            lines,
        )
    }

    @Test
    fun `renderLocationExecutions reports failure when any execution in the group failed`() {
        val executions = listOf(
            ExecutionWithRuleIndex(execution(status = ExecutionStatus(kind = "passed")), emptyMap()),
            ExecutionWithRuleIndex(execution(status = ExecutionStatus(kind = "failed", reason = "broken")), emptyMap()),
        )

        val (_, hasFailure) = renderLocationExecutions(executions)

        assertTrue(hasFailure)
    }

    @Test
    fun `renderLocationExecutions sorts execution blocks by severity - errors, then warns, hints, infos`() {
        fun failed(severity: String, reason: String) =
            ExecutionWithRuleIndex(
                execution(status = ExecutionStatus(kind = "failed", severity = severity, reason = reason)),
                emptyMap(),
            )

        // Deliberately shuffled in the input to prove sorting actually happens.
        val executions = listOf(
            failed("info", "info-message"),
            failed("hint", "hint-message"),
            failed("error", "error-message"),
            failed("warn", "warn-message"),
        )

        val (lines, _) = renderLocationExecutions(executions)
        val statusLines = lines.filter { it.isNotBlank() }

        assertEquals(
            listOf(
                "${ind(2)}✖ error: error-message",
                "${ind(2)}⚠ warn: warn-message",
                "${ind(2)}✎ hint: hint-message",
                "${ind(2)}ℹ info: info-message",
            ),
            statusLines,
        )
    }

    @Test
    fun `renderLocationExecutions keeps relative order for executions with the same severity`() {
        fun failed(reason: String) =
            ExecutionWithRuleIndex(
                execution(status = ExecutionStatus(kind = "failed", severity = "error", reason = reason)),
                emptyMap(),
            )

        val executions = listOf(failed("first"), failed("second"), failed("third"))

        val (lines, _) = renderLocationExecutions(executions)
        val statusLines = lines.filter { it.isNotBlank() }

        assertEquals(
            listOf(
                "${ind(2)}✖ error: first",
                "${ind(2)}✖ error: second",
                "${ind(2)}✖ error: third",
            ),
            statusLines,
        )
    }
}

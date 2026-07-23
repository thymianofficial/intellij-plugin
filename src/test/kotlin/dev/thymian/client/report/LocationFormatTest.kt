package dev.thymian.client.report

import dev.thymian.client.cli.GraphNodeAttributes
import dev.thymian.client.cli.Location
import dev.thymian.client.cli.Report
import dev.thymian.client.cli.SerializedEdge
import dev.thymian.client.cli.SerializedNode
import dev.thymian.client.cli.SerializedThymianFormat
import dev.thymian.client.cli.ToolRun
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mirrors `packages/plugin-reporter/test/resolve-location.test.ts` / the location-resolution
 * behavior of `packages/core/src/report/location-format.ts`, ported to this plugin's Kotlin
 * model.
 */
class LocationFormatTest {

    private val requestNode = SerializedNode(
        key = "req-1",
        attributes = GraphNodeAttributes(type = "http-request", method = "post", path = "/orders", mediaType = ""),
    )
    private val responseNode = SerializedNode(
        key = "res-1",
        attributes = GraphNodeAttributes(type = "http-response", statusCode = 200, mediaType = ""),
    )
    private val transactionEdge = SerializedEdge(key = "tx-1", source = "req-1", target = "res-1")

    private val format = SerializedThymianFormat(
        nodes = listOf(requestNode, responseNode),
        edges = listOf(transactionEdge),
    )

    private fun reportWith(vararg formats: Pair<String, SerializedThymianFormat>, runVersion: String? = formats.firstOrNull()?.first): Report =
        Report(
            reportId = "r",
            createdAt = "1970-01-01T00:00:00.000Z",
            runs = listOf(ToolRun(runId = "run-1", runType = "lint", runAt = "1970-01-01T00:00:00.000Z", thymianFormatVersion = runVersion)),
            thymianFormat = formats.toMap().ifEmpty { null },
        )

    @Test
    fun `resolves a thymianFormat node location to an HTTP request string`() {
        val report = reportWith("v1" to format)
        val resolve = createLocationResolver(report)
        val location = Location.ThymianFormatLocation(elementType = "node", elementId = "req-1", pointer = "")

        assertEquals("POST /orders", resolve(location, "v1"))
    }

    @Test
    fun `resolves a thymianFormat edge location to a request to arrow response string`() {
        val report = reportWith("v1" to format)
        val resolve = createLocationResolver(report)
        val location = Location.ThymianFormatLocation(elementType = "edge", elementId = "tx-1", pointer = "")

        assertEquals("POST /orders → 200 OK", resolve(location, "v1"))
    }

    @Test
    fun `falls back to the single format entry when runVersion is absent`() {
        val report = reportWith("v1" to format, runVersion = null)
        val resolve = createLocationResolver(report)
        val location = Location.ThymianFormatLocation(elementType = "node", elementId = "req-1", pointer = "")

        assertEquals("POST /orders", resolve(location, null))
    }

    @Test
    fun `falls back to the single format entry when runVersion does not match`() {
        val report = reportWith("v1" to format)
        val resolve = createLocationResolver(report)
        val location = Location.ThymianFormatLocation(elementType = "node", elementId = "req-1", pointer = "")

        assertEquals("POST /orders", resolve(location, "unknown-version"))
    }

    @Test
    fun `falls back to a raw format string when no version matches among multiple entries`() {
        val report = reportWith("v1" to format, "v2" to SerializedThymianFormat())
        val resolve = createLocationResolver(report)
        val location = Location.ThymianFormatLocation(elementType = "node", elementId = "missing", pointer = "")

        assertEquals("format:missing", resolve(location, "unknown-version"))
    }

    @Test
    fun `falls back to a raw format string with pointer suffix when unresolvable`() {
        val location = Location.ThymianFormatLocation(elementType = "node", elementId = "abc123", pointer = "/paths/~1orders")

        assertEquals("format:abc123#/paths/~1orders", formatThymianFormatLocation(location, null))
    }

    @Test
    fun `renders a custom location verbatim`() {
        assertEquals("GET /pets", formatLocation(Location.CustomLocation(value = "GET /pets")))
    }

    @Test
    fun `renders a url location verbatim`() {
        assertEquals("https://example.com", formatLocation(Location.UrlLocation(url = "https://example.com")))
    }

    @Test
    fun `renders a file location joining path, line and column`() {
        assertEquals("a.ts:3:5", formatLocation(Location.FileLocation(path = "a.ts", line = 3, column = 5)))
    }

    @Test
    fun `renders a file location with only a path`() {
        assertEquals("a.ts", formatLocation(Location.FileLocation(path = "a.ts")))
    }

    @Test
    fun `appends media type to request and response strings when present`() {
        val requestWithMediaType = SerializedNode(
            key = "req-2",
            attributes = GraphNodeAttributes(type = "http-request", method = "get", path = "/pets", mediaType = "application/json"),
        )
        val report = reportWith("v1" to SerializedThymianFormat(nodes = listOf(requestWithMediaType)))
        val resolve = createLocationResolver(report)
        val location = Location.ThymianFormatLocation(elementType = "node", elementId = "req-2", pointer = "")

        assertEquals("GET /pets - application/json", resolve(location, "v1"))
    }

    @Test
    fun `renders a response with an unknown status code without a trailing space`() {
        val unknownStatusResponse = SerializedNode(
            key = "res-2",
            attributes = GraphNodeAttributes(type = "http-response", statusCode = 418, mediaType = ""),
        )
        val report = reportWith("v1" to SerializedThymianFormat(nodes = listOf(unknownStatusResponse)))
        val resolve = createLocationResolver(report)
        val location = Location.ThymianFormatLocation(elementType = "node", elementId = "res-2", pointer = "")

        assertEquals("418", resolve(location, "v1"))
    }

    @Test
    fun `renders a response with a null status code without a leading space`() {
        val noStatusResponse = SerializedNode(
            key = "res-3",
            attributes = GraphNodeAttributes(type = "http-response", statusCode = null, mediaType = ""),
        )
        val report = reportWith("v1" to SerializedThymianFormat(nodes = listOf(noStatusResponse)))
        val resolve = createLocationResolver(report)
        val location = Location.ThymianFormatLocation(elementType = "node", elementId = "res-3", pointer = "")

        assertEquals("", resolve(location, "v1"))
    }

    @Test
    fun `renders a request with empty method and path without a leading space`() {
        val emptyRequest = SerializedNode(
            key = "req-3",
            attributes = GraphNodeAttributes(type = "http-request", method = "", path = "", mediaType = ""),
        )
        val report = reportWith("v1" to SerializedThymianFormat(nodes = listOf(emptyRequest)))
        val resolve = createLocationResolver(report)
        val location = Location.ThymianFormatLocation(elementType = "node", elementId = "req-3", pointer = "")

        assertEquals("", resolve(location, "v1"))
    }
}

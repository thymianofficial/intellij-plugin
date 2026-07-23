package dev.thymian.client.report

import dev.thymian.client.cli.GraphNodeAttributes
import dev.thymian.client.cli.Location
import dev.thymian.client.cli.Report
import dev.thymian.client.cli.SerializedThymianFormat

/**
 * Kotlin port of `packages/core/src/report/location-format.ts`. Single source of truth for
 * rendering a [Location] to a human-readable string (e.g. `POST /orders`), so the Run tool
 * window output can't drift from the Thymian CLI's rendering of the same [Report].
 */

/** Resolves a [Location] (optionally scoped to a run's `thymianFormatVersion`) to a string. */
internal typealias LocationResolver = (location: Location, runVersion: String?) -> String

private fun fallbackThymianFormatLocation(location: Location.ThymianFormatLocation): String {
    val pointerSuffix = if (location.pointer.isNotEmpty()) "#${location.pointer}" else ""
    return "format:${location.elementId}$pointerSuffix"
}

/**
 * Ported from `packages/core/src/http-status-codes` (`httpStatusCodeToPhrase`).
 * Phrases are lowercase in the TS source; callers uppercase them, matching
 * `thymianResponseToString`. Unknown codes resolve to an empty phrase there.
 */
private val httpStatusCodeToPhrase: Map<Int, String> = mapOf(
    // 1xx informational
    100 to "continue",
    101 to "switching protocols",
    102 to "processing",
    103 to "early hints",
    // 2xx successful
    200 to "ok",
    201 to "created",
    202 to "accepted",
    203 to "non-authoritative information",
    204 to "no content",
    205 to "reset content",
    206 to "partial content",
    207 to "multi-status",
    208 to "already reported",
    // 3xx redirection
    300 to "multiple choices",
    301 to "moved permanently",
    302 to "found",
    303 to "see other",
    304 to "not modified",
    307 to "temporary redirect",
    308 to "permanent redirect",
    // 4xx client error
    400 to "bad request",
    401 to "unauthorized",
    402 to "payment required",
    403 to "forbidden",
    404 to "not found",
    405 to "method not allowed",
    406 to "not acceptable",
    407 to "proxy authentication required",
    408 to "request timeout",
    409 to "conflict",
    410 to "gone",
    411 to "length required",
    412 to "precondition failed",
    413 to "payload too large",
    414 to "uri too long",
    415 to "unsupported media type",
    416 to "range not satisfiable",
    417 to "expectation failed",
    421 to "misdirected request",
    422 to "unprocessable entity",
    423 to "locked",
    424 to "failed dependency",
    425 to "too early",
    426 to "upgrade required",
    428 to "precondition required",
    429 to "too many requests",
    431 to "request header fields too large",
    451 to "unavailable for legal reasons",
    // 5xx server error
    500 to "internal server error",
    501 to "not implemented",
    502 to "bad gateway",
    503 to "service unavailable",
    504 to "gateway timeout",
    505 to "http version not supported",
    506 to "variant also negotiates",
    507 to "insufficient storage",
    508 to "loop detected",
    510 to "not extended",
    511 to "network authentication required",
)

private fun isHttpRequest(attributes: GraphNodeAttributes) = attributes.type == "http-request"
private fun isHttpResponse(attributes: GraphNodeAttributes) = attributes.type == "http-response"

private fun thymianRequestToString(attributes: GraphNodeAttributes): String {
    val title = listOfNotNull(
        attributes.method?.uppercase()?.ifEmpty { null },
        attributes.path?.ifEmpty { null },
    ).joinToString(" ")
    return if (!attributes.mediaType.isNullOrEmpty()) "$title - ${attributes.mediaType}" else title
}

private fun thymianResponseToString(attributes: GraphNodeAttributes): String {
    val statusCode = attributes.statusCode
    val phrase = statusCode?.let { httpStatusCodeToPhrase[it] }.orEmpty()
    val title = listOfNotNull(
        statusCode?.toString(),
        phrase.uppercase().ifEmpty { null },
    ).joinToString(" ")
    return if (!attributes.mediaType.isNullOrEmpty()) "$title - ${attributes.mediaType}" else title
}

private fun thymianHttpTransactionToString(request: GraphNodeAttributes, response: GraphNodeAttributes): String =
    "${thymianRequestToString(request)} → ${thymianResponseToString(response)}"

/**
 * Render a `thymianFormat` location against an already-resolved [SerializedThymianFormat],
 * falling back to the raw `format:{elementId}` form when the format is absent or the
 * node/edge can't be resolved to an HTTP request/response/transaction.
 */
internal fun formatThymianFormatLocation(
    location: Location.ThymianFormatLocation,
    format: SerializedThymianFormat?,
): String {
    if (format == null) {
        return fallbackThymianFormatLocation(location)
    }

    if (location.elementType == "node") {
        val node = format.nodes.find { it.key == location.elementId }?.attributes ?: return fallbackThymianFormatLocation(location)

        return when {
            isHttpRequest(node) -> thymianRequestToString(node)
            isHttpResponse(node) -> thymianResponseToString(node)
            else -> fallbackThymianFormatLocation(location)
        }
    }

    val edge = format.edges.find { it.key == location.elementId } ?: return fallbackThymianFormatLocation(location)
    val request = format.nodes.find { it.key == edge.source }?.attributes
    val response = format.nodes.find { it.key == edge.target }?.attributes

    return if (request != null && response != null) {
        thymianHttpTransactionToString(request, response)
    } else {
        fallbackThymianFormatLocation(location)
    }
}

/** Render any report [Location] (not just `thymianFormat`) to a string. */
internal fun formatLocation(location: Location, format: SerializedThymianFormat? = null): String = when (location) {
    is Location.CustomLocation -> location.value
    is Location.FileLocation -> listOfNotNull(location.path, location.line, location.column).joinToString(":")
    is Location.UrlLocation -> location.url
    is Location.ThymianFormatLocation -> formatThymianFormatLocation(location, format)
}

/**
 * Resolve the [SerializedThymianFormat] a run used, from `report.thymianFormat`. Falls back
 * to the single entry when `runVersion` is absent/unmatched and there is exactly one format
 * in the report — this guards against a producer plugin forgetting to set
 * `ToolRun.thymianFormatVersion`. Returns `null` (never throws) when no format can be
 * resolved, so a single bad/missing entry can't fail an entire render.
 */
internal fun resolveThymianFormatForRun(
    formats: Map<String, SerializedThymianFormat>?,
    runVersion: String?,
): SerializedThymianFormat? {
    if (formats.isNullOrEmpty()) {
        return null
    }

    return if (runVersion != null && formats.containsKey(runVersion)) {
        formats[runVersion]
    } else if (formats.size == 1) {
        formats.values.single()
    } else {
        null
    }
}

private const val SINGLE_ENTRY_CACHE_KEY = "\u0000single-entry"

/**
 * Build a caching [LocationResolver] for a whole report: resolves and caches the
 * [SerializedThymianFormat] per `runVersion` (via [resolveThymianFormatForRun]) and renders
 * locations against it (via [formatLocation]). Suited to callers that render many locations
 * across potentially many run versions.
 */
internal fun createLocationResolver(report: Report): LocationResolver {
    val formatCache = mutableMapOf<String, SerializedThymianFormat?>()

    return resolver@{ location, runVersion ->
        if (location !is Location.ThymianFormatLocation) {
            return@resolver formatLocation(location)
        }

        val cacheKey = runVersion ?: SINGLE_ENTRY_CACHE_KEY
        val format = if (formatCache.containsKey(cacheKey)) {
            formatCache[cacheKey]
        } else {
            resolveThymianFormatForRun(report.thymianFormat, runVersion).also { formatCache[cacheKey] = it }
        }

        formatThymianFormatLocation(location, format)
    }
}

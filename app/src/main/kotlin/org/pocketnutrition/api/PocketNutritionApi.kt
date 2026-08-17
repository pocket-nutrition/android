// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.api

import org.pocketnutrition.core.IngredientSuggestion
import org.pocketnutrition.core.ServingUnit
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The two endpoints the app needs, and the shapes they exchange. Transcribed from the vendored
// contract/data/openapi.patched.json rather than from a live response, so a field the server stops
// sending fails to decode here instead of quietly becoming null somewhere in a composable.
//
// WHY HttpURLConnection AND NOT OkHttp OR KTOR
// This module has four dependencies and a version catalogue whose comment says every version is
// declared in one place. Adding an HTTP stack for two GET/POST calls against one host is not worth
// that; HttpURLConnection is in the platform, and kotlinx.serialization — already a dependency, and
// already annotated in the catalogue as "later the API responses" — does the decoding. Revisit when
// the app needs interceptors, connection reuse across screens, or streaming.

// IngredientSuggestion, RecipeComponent, AddItem and MealIngredient now live in the core package,
// because build-meal-ingredient, apply-method-change and unpack-recipe all take those shapes as input.
// They sat here while those three suites were unported, which read as a layering decision but was only
// ever an ordering one.

/** One item of a `POST /nutrition` request body. */
@Serializable
data class FoodItemRequest(
    val name: String?,
    val quantity: Double,
    val unit: String,
    val cookingMethod: String,
    val measuredState: String,
    val ingredientId: String?,
)

/**
 * One item of a `POST /nutrition` response.
 *
 * Only the fields the results screen renders. Adding another nutrient is a one-line change; carrying
 * all thirty unused would obscure which ones the product actually shows.
 */
@Serializable
data class NutritionalProfile(
    val name: String,
    val quantity: Double,
    val unit: String,
    val cookingMethod: String,
    val measuredState: String,
    val energyKcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbohydratesG: Double,
    val fiberG: Double? = null,
    val sugarsG: Double? = null,
)

/** One correction inside a `POST /feedback` request body. */
@Serializable
data class FeedbackCorrection(
    val category: String,
    val code: String,
    val value: String? = null,
    val unit: String? = null,
)

/**
 * A `POST /feedback` request body: the ingredient being reported, its corrections and/or a free
 * comment, and the cooking context the report was made from.
 *
 * [cookingMethod], [measuredState] and [source] are omitted rather than sent as empty strings when
 * unknown at the call site — a client-minted value is worse than an absent optional field, same
 * rule the sandbox's `postFeedback` follows.
 */
@Serializable
data class FeedbackRequest(
    val name: String,
    val corrections: List<FeedbackCorrection>,
    val comment: String? = null,
    val cookingMethod: String? = null,
    val measuredState: String? = null,
    val source: String? = null,
)

/** Reference macro-nutrient values per 100 g, inside a `GET /ingredients/{id}` response. */
@Serializable
data class ReferenceNutrition(
    val energyKcal: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val carbohydratesG: Double? = null,
    val fiberG: Double? = null,
)

/**
 * A `GET /ingredients/{id}` response: the knowledge-base detail behind one ingredient or product —
 * the current known values the feedback form shows beside a correction row. Only the fields the
 * feedback sheet reads are kept; see [ReferenceNutrition] for the same rule.
 */
@Serializable
data class IngredientDetail(
    val ingredientId: String,
    val nameFr: String? = null,
    val nameEn: String? = null,
    val aliasesFr: List<String> = emptyList(),
    val aliasesEn: List<String> = emptyList(),
    val foodGroup: String? = null,
    val referenceWeightG: Double? = null,
    val displayUnit: String? = null,
    val servingUnits: List<ServingUnit> = emptyList(),
    val canEatRaw: Boolean? = null,
    val canEatCooked: Boolean? = null,
    val typicalState: String? = null,
    val referenceNutrition: ReferenceNutrition? = null,
)

/**
 * Failures a caller can act on differently, rather than one opaque exception.
 *
 * Each message is French and user-facing: the copy deck puts the raw message in front of the user via
 * `errors.prefix` — "Erreur : {message}" — so an English exception string would land inside French
 * copy.
 */
sealed class ApiException(val frenchMessage: String) : Exception(frenchMessage) {
    object Offline : ApiException("pas de connexion internet")
    object TimedOut : ApiException("le serveur ne répond pas")
    class BadStatus(code: Int) : ApiException("le serveur a répondu $code")
    object Malformed : ApiException("réponse illisible du serveur")
}

/** Client for the public Pocket Nutrition API. */
object PocketNutritionApi {

    /**
     * The public API. No staging host and no build flavour: there is one deployment, and a base URL
     * that can be pointed elsewhere invites shipping a build pointed at the wrong place.
     */
    private const val BASE_URL = "https://api.pocketnutrition.org"

    /** A phone on a weak connection should get a retryable error, not a hang. */
    private const val TIMEOUT_MS = 15_000

    /**
     * How many ingredient phrases the server split the query into. Documented by the contract overlay
     * because springdoc omits response headers, which is why no generated client exposes it.
     */
    private const val SEGMENT_COUNT_HEADER = "X-Segment-Count"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * A search response, rows and segment count together.
     *
     * The count travels with the rows because it **cannot be derived from them**, and it cannot be
     * derived from the query either: the server segments with greedy longest-match probes against the
     * search index, so `pomme de terre` is one term while `riz poulet pomme` is three. A client counting
     * spaces would offer a multi-select for a single potato.
     *
     * `segmentCount` defaults to 1 when the header is absent or unparseable. That default is
     * load-bearing rather than defensive: an older deployment, or a proxy that strips the header, must
     * degrade to single-add behaviour rather than to a multi-select nobody can confirm. Same
     * tolerant-reader rule the contract already states for enum values, applied to a header.
     */
    data class SearchResponse(val rows: List<IngredientSuggestion>, val segmentCount: Int)

    /**
     * `GET /ingredients/search`. A blank query returns an empty result without calling the server: the
     * field fires as the user types, and `q` is required by the spec.
     */
    suspend fun search(query: String, limit: Int = 12): SearchResponse {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return SearchResponse(emptyList(), segmentCount = 1)
        val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
        val url = "$BASE_URL/ingredients/search?q=$encoded&lang=fr&limit=$limit"
        val raw = requestWithHeaders("GET", url, body = null, wantedHeaders = setOf(SEGMENT_COUNT_HEADER))
        return SearchResponse(
            rows = json.decodeFromString(raw.body),
            segmentCount = raw.headers[SEGMENT_COUNT_HEADER]?.toIntOrNull()?.takeIf { it > 0 } ?: 1,
        )
    }

    /**
     * `POST /nutrition`. The whole meal goes in one body, which is what the endpoint takes and what
     * keeps a multi-item meal to a single round trip.
     */
    suspend fun analyse(items: List<FoodItemRequest>): List<NutritionalProfile> {
        val body = json.encodeToString(items)
        return json.decodeFromString(request("POST", "$BASE_URL/nutrition", body))
    }

    /**
     * `POST /feedback`. The response body is an untyped bag (`additionalProperties: string`) that
     * nothing here reads, so the call reports success or throws rather than decoding a value.
     */
    suspend fun submitFeedback(feedback: FeedbackRequest) {
        request("POST", "$BASE_URL/feedback", json.encodeToString(feedback))
    }

    /**
     * `GET /ingredients/barcode/{ean13}`. `null` means the barcode is not in the Open Food Facts
     * catalogue — an expected, distinct outcome the spec documents on its own 404 response, not a
     * server error, which is why this does not throw [ApiException.BadStatus] for it.
     *
     * `HttpURLConnection.getInputStream()` throws `FileNotFoundException` for any 4xx/5xx status; the
     * body for those comes from `getErrorStream()` instead. Every other method in this object avoids
     * needing to know that, because none of them ever reads a body off a non-2xx response — this is
     * the first one that does, since the 404 body here carries the same shape as the 200 body (per the
     * contract) rather than being discarded.
     *
     * Decodes straight into the existing [IngredientSuggestion]: only `ingredientId`/`label` are
     * required there, everything else is nullable/defaulted, and [json] already ignores the extra
     * `nutrition` field the response schema (`IngredientSuggestionWithNutrition`) adds on top.
     */
    suspend fun lookupBarcode(ean13: String): IngredientSuggestion? = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(ean13, "UTF-8")
        val url = "$BASE_URL/ingredients/barcode/$encoded"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            if (status != 404 && status !in 200..299) throw ApiException.BadStatus(status)
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val payload = stream.use { it.readBytes().toString(Charsets.UTF_8) }
            if (status == 404) null else json.decodeFromString<IngredientSuggestion>(payload)
        } catch (e: UnknownHostException) {
            throw ApiException.Offline
        } catch (e: SocketTimeoutException) {
            throw ApiException.TimedOut
        } catch (e: SerializationException) {
            throw ApiException.Malformed
        } catch (e: IOException) {
            throw ApiException.Offline
        } finally {
            connection.disconnect()
        }
    }

    /**
     * `GET /ingredients/{id}`. `null` means the id is unknown to the server — an expected outcome
     * (the feedback sheet treats it as "no extra hints available", not an error), same reasoning
     * and same 404-carries-no-body shape as [lookupBarcode].
     */
    suspend fun ingredientDetail(id: String): IngredientDetail? = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(id, "UTF-8")
        val url = "$BASE_URL/ingredients/$encoded"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            if (status != 404 && status !in 200..299) throw ApiException.BadStatus(status)
            if (status == 404) return@withContext null
            val payload = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            json.decodeFromString<IngredientDetail>(payload)
        } catch (e: UnknownHostException) {
            throw ApiException.Offline
        } catch (e: SocketTimeoutException) {
            throw ApiException.TimedOut
        } catch (e: SerializationException) {
            throw ApiException.Malformed
        } catch (e: IOException) {
            throw ApiException.Offline
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun request(method: String, url: String, body: String?): String =
        requestWithHeaders(method, url, body).body

    /** A response body plus the headers a caller asked to keep. */
    private data class RawResponse(val body: String, val headers: Map<String, String>)

    /**
     * The same transport as [request], returning the headers listed in [wantedHeaders] alongside the body.
     *
     * Split out rather than making every caller deal with headers: only search needs one. The headers are
     * read **inside** the try, before the `finally` calls `disconnect()` — `getHeaderField` on a
     * disconnected connection returns null with no error, so reading them afterwards would silently
     * produce a client that always behaves as though the server sent nothing.
     */
    private suspend fun requestWithHeaders(
        method: String,
        url: String,
        body: String?,
        wantedHeaders: Set<String> = emptySet(),
    ): RawResponse =
        withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            try {
                if (body != null) {
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                if (status !in 200..299) throw ApiException.BadStatus(status)
                val headers = wantedHeaders.mapNotNull { name ->
                    connection.getHeaderField(name)?.let { name to it }
                }.toMap()
                val payload = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                RawResponse(payload, headers)
            } catch (e: UnknownHostException) {
                // Mapped rather than rethrown: these are the cases a user can act on, and they are
                // the ones that reach the error banner.
                throw ApiException.Offline
            } catch (e: SocketTimeoutException) {
                throw ApiException.TimedOut
            } catch (e: SerializationException) {
                throw ApiException.Malformed
            } catch (e: IOException) {
                throw ApiException.Offline
            } finally {
                connection.disconnect()
            }
        }
}

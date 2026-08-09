package com.example.velopath.core.search

import com.example.velopath.BuildConfig
import com.yandex.mapkit.geometry.Point
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class YandexPlacesSearchService {
    private val suggestCache = LinkedHashMap<String, List<PlaceSearchResult>>()
    private val resolveCache = LinkedHashMap<String, PlaceSearchResult>()

    suspend fun suggest(
        query: String,
        userPoint: Point?,
        sessionToken: String
    ): Result<List<PlaceSearchResult>> = withContext(Dispatchers.IO) {
        if (BuildConfig.SEARCH_API_KEY.isBlank()) {
            return@withContext Result.failure(IllegalStateException("SEARCH_API_KEY is missing"))
        }

        if (query.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        val suggestKey = buildString {
            append(query.trim().lowercase())
            userPoint?.let {
                append("|")
                append(String.format(Locale.US, "%.4f,%.4f", it.latitude, it.longitude))
            }
        }
        suggestCache[suggestKey]?.let { return@withContext Result.success(it) }

        runCatching {
            val encodedQuery = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
            val pointParams = userPoint?.let {
                "&ull=${it.longitude},${it.latitude}&ll=${it.longitude},${it.latitude}&spn=0.35,0.35"
            }.orEmpty()

            val url = URL(
                "https://suggest-maps.yandex.ru/v1/suggest" +
                    "?apikey=${BuildConfig.SEARCH_API_KEY}" +
                    "&text=$encodedQuery" +
                    "&lang=ru_RU" +
                    "&results=6" +
                    "&types=geo,biz" +
                    "&print_address=1" +
                    "&attrs=uri" +
                    "&sessiontoken=$sessionToken" +
                    pointParams
            )

            val body = executeRequest(url)
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: JSONArray()

            buildList {
                for (index in 0 until results.length()) {
                    val item = results.optJSONObject(index) ?: continue
                    val title = item.optJSONObject("title")
                    val subtitle = item.optJSONObject("subtitle")
                    val tags = item.optJSONArray("tags")

                    add(
                        PlaceSearchResult(
                            title = title?.optString("text").orEmpty().ifBlank { "Без названия" },
                            subtitle = buildSubtitle(
                                subtitle = subtitle?.optString("text").orEmpty(),
                                tags = tags
                            ),
                            uri = item.optString("uri").ifBlank { null }
                        )
                    )
                }
            }.also { cached ->
                if (suggestCache.size >= 24) {
                    suggestCache.remove(suggestCache.entries.first().key)
                }
                suggestCache[suggestKey] = cached
            }
        }
    }

    suspend fun resolve(result: PlaceSearchResult): Result<PlaceSearchResult> = withContext(Dispatchers.IO) {
        if (BuildConfig.GEOCODER_API_KEY.isBlank()) {
            return@withContext Result.failure(IllegalStateException("GEOCODER_API_KEY is missing"))
        }

        if (result.latitude != null && result.longitude != null) {
            return@withContext Result.success(result)
        }

        val resolveKey = result.uri ?: "${result.title}|${result.subtitle}"
        resolveCache[resolveKey]?.let { return@withContext Result.success(it) }

        runCatching {
            resolveViaGeocoder(result)
                ?: resolveViaTextFallback(result)
                ?: error("No coordinates found for selected suggestion")
        }.map { resolved ->
            if (resolveCache.size >= 24) {
                resolveCache.remove(resolveCache.entries.first().key)
            }
            resolveCache[resolveKey] = resolved
            resolved
        }
    }

    private fun resolveViaGeocoder(result: PlaceSearchResult): PlaceSearchResult? {
        val query = buildString {
            append("https://geocode-maps.yandex.ru/1.x/")
            append("?apikey=${BuildConfig.GEOCODER_API_KEY}")
            append("&lang=ru_RU")
            append("&format=json")
            append("&results=1")
            if (!result.uri.isNullOrBlank()) {
                append("&uri=${URLEncoder.encode(result.uri, Charsets.UTF_8.name())}")
            } else {
                val text = listOf(result.title, result.subtitle)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                append("&geocode=${URLEncoder.encode(text, Charsets.UTF_8.name())}")
            }
        }

        return parseGeocoderResponse(
            body = executeRequest(URL(query)),
            original = result
        )
    }

    private fun resolveViaTextFallback(result: PlaceSearchResult): PlaceSearchResult? {
        val text = listOf(result.title, result.subtitle)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { return null }

        val url = URL(
            "https://geocode-maps.yandex.ru/1.x/" +
                "?apikey=${BuildConfig.GEOCODER_API_KEY}" +
                "&geocode=${URLEncoder.encode(text, Charsets.UTF_8.name())}" +
                "&lang=ru_RU" +
                "&format=json" +
                "&results=1"
        )

        return parseGeocoderResponse(executeRequest(url), result)
    }

    private fun parseGeocoderResponse(
        body: String,
        original: PlaceSearchResult
    ): PlaceSearchResult? {
        val json = JSONObject(body)
        val members = json
            .optJSONObject("response")
            ?.optJSONObject("GeoObjectCollection")
            ?.optJSONArray("featureMember")
            ?: JSONArray()

        val geoObject = members.optJSONObject(0)?.optJSONObject("GeoObject")
            ?: return null
        val position = geoObject
            .optJSONObject("Point")
            ?.optString("pos")
            ?.split(" ")
            ?: return null

        val longitude = position.getOrNull(0)?.toDoubleOrNull() ?: return null
        val latitude = position.getOrNull(1)?.toDoubleOrNull() ?: return null
        val description = geoObject
            .optJSONObject("metaDataProperty")
            ?.optJSONObject("GeocoderMetaData")
            ?.optString("text")
            .orEmpty()

        return original.copy(
            subtitle = description.ifBlank { original.subtitle },
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun executeRequest(url: URL): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            doInput = true
            useCaches = true
        }

        try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = BufferedReader(InputStreamReader(stream)).use { reader ->
                buildString {
                    while (true) {
                        val line = reader.readLine() ?: break
                        append(line)
                    }
                }
            }

            if (responseCode !in 200..299) {
                val message = runCatching { JSONObject(body).optString("message") }
                    .getOrNull()
                    .orEmpty()
                val details = if (message.isBlank()) body.take(220) else message
                error("HTTP $responseCode: $details")
            }

            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun buildSubtitle(subtitle: String, tags: JSONArray?): String {
        val tagText = buildList {
            if (tags != null) {
                for (index in 0 until tags.length()) {
                    tags.optString(index)?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.joinToString(", ")

        return listOf(subtitle, tagText)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }
}

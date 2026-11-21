package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GeminiApiException(
    val statusCode: Int,
    override val message: String
) : Exception(message)

/**
 * Direct Gemini Developer REST API client using `BuildConfig.GEMINI_API_KEY` injected via
 * the Secrets Gradle Plugin (AI Studio Secrets panel). Supports multimodal text, images,
 * and WAV audio with strict JSON schema output and automatic high-demand failover.
 */
object GeminiDirectApiClient {

    private const val TAG = "GeminiDirectAPI"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val PLACEHOLDER_VALUE = "GEMINI_API_KEY_DEFAULT_VALUE"

    const val PRIMARY_MODEL_NAME = "gemini-3.8-flash"
    const val FALLBACK_MODEL_NAME = "gemini-3.5-flash-lite"

    private const val PRIMARY_TIMEOUT_MILLIS = 35_000L
    private const val FALLBACK_TIMEOUT_MILLIS = 35_000L

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    fun getConfiguredApiKey(): String {
        val raw = BuildConfig.GEMINI_API_KEY.trim()
        return if (raw.isBlank() || raw == PLACEHOLDER_VALUE) "" else raw
    }

    suspend fun generateStructuredJson(
        instructionBlock: String,
        responseSchema: JSONObject,
        image: Bitmap? = null,
        audioBytes: ByteArray? = null,
        audioMimeType: String = VoiceAudioRecorder.AUDIO_MIME_TYPE,
        onProgress: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getConfiguredApiKey()
        if (apiKey.isEmpty()) {
            throw IllegalStateException(
                "Gemini API key is not configured yet. Add GEMINI_API_KEY in the AI Studio Secrets panel, then rebuild the app."
            )
        }

        val requestPayload = buildRequestBodyJson(
            instructionBlock = instructionBlock,
            responseSchema = responseSchema,
            image = image,
            audioBytes = audioBytes,
            audioMimeType = audioMimeType
        )

        val attempts = listOf(
            PRIMARY_MODEL_NAME to PRIMARY_TIMEOUT_MILLIS,
            FALLBACK_MODEL_NAME to FALLBACK_TIMEOUT_MILLIS,
            FALLBACK_MODEL_NAME to FALLBACK_TIMEOUT_MILLIS
        )

        var lastError: Exception? = null
        attempts.forEachIndexed { index, (modelName, timeoutMs) ->
            if (index > 0) {
                val backoffMs = if (index == 1) 400L else 1_200L
                onProgress(
                    if (index == 1) {
                        "Switching to $FALLBACK_MODEL_NAME…"
                    } else {
                        "High demand — retrying automatically…"
                    }
                )
                delay(backoffMs)
            }
            try {
                val resultText = executeSingleModelCall(
                    apiKey = apiKey,
                    modelName = modelName,
                    requestPayload = requestPayload,
                    timeoutMillis = timeoutMs
                )
                onProgress("Preparing actions…")
                return@withContext resultText
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    Log.w(TAG, "$modelName timed out on attempt ${index + 1}", e)
                    lastError = e
                } else {
                    throw e
                }
            } catch (e: Exception) {
                lastError = e
                if (!isRetryableError(e)) {
                    throw e
                }
                Log.w(TAG, "$modelName transient error on attempt ${index + 1}: ${e.localizedMessage}", e)
            }
        }

        throw lastError ?: IllegalStateException("Gemini is temporarily busy. Please try again.")
    }

    private fun buildRequestBodyJson(
        instructionBlock: String,
        responseSchema: JSONObject,
        image: Bitmap?,
        audioBytes: ByteArray?,
        audioMimeType: String
    ): String {
        val parts = JSONArray()

        if (audioBytes != null && audioBytes.isNotEmpty()) {
            val encodedAudio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", audioMimeType)
                        .put("data", encodedAudio)
                )
            )
        }

        if (image != null) {
            val jpegStream = ByteArrayOutputStream()
            image.compress(Bitmap.CompressFormat.JPEG, 85, jpegStream)
            val encodedImage = Base64.encodeToString(jpegStream.toByteArray(), Base64.NO_WRAP)
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", encodedImage)
                )
            )
        }

        parts.put(JSONObject().put("text", instructionBlock))

        val contents = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("parts", parts)
        )

        val generationConfig = JSONObject()
            .put("responseMimeType", "application/json")
            .put("responseSchema", responseSchema)
            .put("temperature", 0.2)
            .put("maxOutputTokens", 2048)

        return JSONObject()
            .put("contents", contents)
            .put("generationConfig", generationConfig)
            .toString()
    }

    private suspend fun executeSingleModelCall(
        apiKey: String,
        modelName: String,
        requestPayload: String,
        timeoutMillis: Long
    ): String = withTimeout(timeoutMillis) {
        val startMs = System.currentTimeMillis()
        val url = "$BASE_URL/$modelName:generateContent"
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(requestPayload.toRequestBody(jsonMediaType))
            .build()

        val responsePair = executeCallSuspend(httpClient.newCall(request))
        val statusCode = responsePair.first
        val rawBody = responsePair.second

        if (statusCode !in 200..299) {
            val parsedMessage = parseApiErrorMessage(rawBody)
                ?: "Gemini API returned HTTP $statusCode."
            throw GeminiApiException(statusCode, parsedMessage)
        }

        val text = extractCandidateText(rawBody)
        if (text.isBlank()) {
            throw IllegalStateException("Gemini returned an empty response. Please try again.")
        }

        Log.d(TAG, "$modelName completed in ${System.currentTimeMillis() - startMs}ms (${text.length} chars)")
        text
    }

    private suspend fun executeCallSuspend(call: Call): Pair<Int, String> =
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Exception) {
                }
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        val bodyString = try {
                            res.body?.string().orEmpty()
                        } catch (e: Exception) {
                            ""
                        }
                        if (cont.isActive) {
                            cont.resume(res.code to bodyString)
                        }
                    }
                }
            })
        }

    private fun extractCandidateText(rawBody: String): String {
        val root = JSONObject(rawBody)
        val candidates = root.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val content = candidates.optJSONObject(0)?.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        val out = StringBuilder()
        for (i in 0 until parts.length()) {
            val partText = parts.optJSONObject(i)?.optString("text", "").orEmpty()
            if (partText.isNotEmpty()) out.append(partText)
        }
        return out.toString().trim()
    }

    private fun parseApiErrorMessage(rawBody: String): String? {
        if (rawBody.isBlank()) return null
        return try {
            val root = JSONObject(rawBody)
            root.optJSONObject("error")?.optString("message")?.trim()?.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun isRetryableError(error: Exception): Boolean {
        if (error is GeminiApiException) {
            // Hard key/auth errors should fail fast and surface clearly
            if (error.statusCode == 401 || error.statusCode == 403) return false
            if (error.statusCode == 400 && error.message.lowercase().contains("api key")) return false
            if (error.statusCode in setOf(404, 408, 429, 500, 502, 503, 504)) return true
        }
        val msg = (error.localizedMessage ?: error.message ?: "").lowercase()
        if ("api key" in msg || "api_key_invalid" in msg || "permission_denied" in msg) {
            return false
        }
        return "high demand" in msg ||
            "demand" in msg ||
            "try again" in msg ||
            "temporarily" in msg ||
            "overloaded" in msg ||
            "busy" in msg ||
            "capacity" in msg ||
            "unavailable" in msg ||
            "resource exhausted" in msg ||
            "resource_exhausted" in msg ||
            "rate limit" in msg ||
            "429" in msg ||
            "500" in msg ||
            "502" in msg ||
            "503" in msg ||
            "504" in msg ||
            "404" in msg ||
            "not found" in msg ||
            "timeout" in msg ||
            "timed out" in msg ||
            "internal" in msg
    }
}

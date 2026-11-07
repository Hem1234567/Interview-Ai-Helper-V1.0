package com.interviewcopilot.app.logic

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

interface LlmProvider {
    val name: String
    val firstTokenTimeoutMs: Long
    fun stream(system: String, user: String, maxTokens: Int): Flow<String>
}

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

private fun JSONObject.str(key: String): String =
    if (isNull(key)) "" else optString(key, "")

class OpenRouterProvider(private val key: String, private val model: String) : LlmProvider {
    override val name = "OpenRouter"
    override val firstTokenTimeoutMs = 6000L

    override fun stream(system: String, user: String, maxTokens: Int): Flow<String> = flow {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        val body = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("max_tokens", maxTokens)
            .put("temperature", 0.4)
            .put("messages", messages)
        val req = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("HTTP-Referer", "https://interviewcopilot.app")
            .addHeader("X-Title", "Interview Copilot")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val call = Http.client.newCall(req)
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val msg = resp.body?.string()?.take(200) ?: ""
                    throw IOException("OpenRouter error ${resp.code}: $msg")
                }
                val src = resp.body!!.source()
                while (!src.exhausted()) {
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val piece = try {
                        val choice = JSONObject(data).getJSONArray("choices").getJSONObject(0)
                        choice.getJSONObject("delta").str("content")
                    } catch (e: Exception) {
                        ""
                    }
                    if (piece.isNotEmpty()) emit(piece)
                }
            }
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)
}

class GeminiProvider(private val key: String, private val model: String) : LlmProvider {
    override val name = "Gemini"
    override val firstTokenTimeoutMs = 6000L

    override fun stream(system: String, user: String, maxTokens: Int): Flow<String> = flow {
        val body = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", user)))
                )
            )
            .put(
                "generationConfig",
                JSONObject().put("maxOutputTokens", maxTokens).put("temperature", 0.4)
            )
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$key"
        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val call = Http.client.newCall(req)
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val msg = resp.body?.string()?.take(200) ?: ""
                    throw IOException("Gemini error ${resp.code}: $msg")
                }
                val src = resp.body!!.source()
                while (!src.exhausted()) {
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    val piece = try {
                        val parts = JSONObject(data).getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts")
                        val sb = StringBuilder()
                        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).str("text"))
                        sb.toString()
                    } catch (e: Exception) {
                        ""
                    }
                    if (piece.isNotEmpty()) emit(piece)
                }
            }
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)
}

object LocalEngine {
    private var llm: LlmInference? = null
    private var loadedPath: String? = null

    @Synchronized
    fun get(ctx: Context, path: String): LlmInference {
        val current = llm
        if (current != null && loadedPath == path) return current
        current?.close()
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(1024)
            .build()
        val created = LlmInference.createFromOptions(ctx.applicationContext, options)
        llm = created
        loadedPath = path
        return created
    }

    @Synchronized
    fun reset() {
        try {
            llm?.close()
        } catch (ignored: Exception) {
        }
        llm = null
        loadedPath = null
    }
}

class LocalProvider(
    private val ctx: Context,
    private val path: String,
    private val modelName: String
) : LlmProvider {
    override val name = "Local"
    override val firstTokenTimeoutMs = 45000L

    override fun stream(system: String, user: String, maxTokens: Int): Flow<String> = callbackFlow {
        val engine = LocalEngine.get(ctx, path)
        val prompt = if (modelName.contains("gemma", ignoreCase = true)) {
            "<start_of_turn>user\n$system\n\n$user<end_of_turn>\n<start_of_turn>model\n"
        } else {
            "$system\n\n$user\n\nAnswer:"
        }
        val listener = ProgressListener<String> { partial, done ->
            if (!partial.isNullOrEmpty()) trySend(partial)
            if (done) close()
        }
        engine.generateResponseAsync(prompt, listener)
        awaitClose { }
    }.flowOn(Dispatchers.IO)
}

package org.textrpg.application.adapter.llm

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.textrpg.application.adapter.llm.model.LLMMessage
import org.textrpg.application.data.config.AppConfig

/**
 * OpenAI 兼容的 LLM 客户端
 *
 * 实现 LLMClient 接口，使用 OpenAI Chat Completions API 格式
 */
class OpenAIClient(
    private val config: AppConfig,
    private val httpClient: HttpClient,
) : LLMClient {
    private val gson: Gson = Gson()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 生成文本
     *
     * 向 OpenAI 兼容 API 发送消息列表，获取生成的文本响应
     *
     * @param messages 消息列表，包含 System、Assistant、User 消息
     * @return Result，包含生成的文本或错误信息
     */
    override suspend fun generate(messages: List<LLMMessage>): Result<String> {
        if (!config.llm.enabled) {
            return Result.failure(Exception("LLM is disabled"))
        }

        return try {
            val requestBody = JsonObject().apply {
                addProperty("model", config.llm.model)
                add("messages", JsonArray().apply {
                    messages.forEach { add(it.toJson()) }
                })
                addProperty("stream", false)
            }

            val response = httpClient.post(config.llm.apiUrl) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${config.llm.apiKey}")
                setBody(gson.toJson(requestBody))
            }

            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val json = gson.fromJson(body, JsonObject::class.java)
                val responseText = json
                    .getAsJsonArray("choices")
                    ?.firstOrNull()
                    ?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")
                    ?.asString
                    ?: ""
                Result.success(responseText)
            } else {
                Result.failure(Exception("LLM request failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun shutdown() {
        scope.cancel()
    }

    private fun LLMMessage.toJson(): JsonObject = when (this) {
        is LLMMessage.System -> JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", message)
        }
        is LLMMessage.Assistant -> JsonObject().apply {
            addProperty("role", "assistant")
            addProperty("content", message)
        }
        is LLMMessage.User -> JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", message)
        }
    }
}

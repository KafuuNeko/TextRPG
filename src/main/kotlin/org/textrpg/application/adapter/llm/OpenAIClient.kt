package org.textrpg.application.adapter.llm

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
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
            return Result.failure(IllegalStateException("LLM is disabled"))
        }

        return runCatching {
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

            if (!response.status.isSuccess()) {
                error("LLM request failed: ${response.status}")
            }

            val body = response.bodyAsText()
            gson.fromJson(body, JsonObject::class.java)
                .getAsJsonArray("choices")
                ?.firstOrNull()
                ?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")
                ?.asString
                ?: ""
        }
    }

    /**
     * 关闭客户端
     *
     * `OpenAIClient` 持有的所有资源（[httpClient] / [config]）都是构造期注入的，
     * 生命周期由调用方（通常是 Application）管理，本实现无需自行释放——故为 no-op。
     * 保留方法是为了满足 [LLMClient.shutdown] 接口契约。
     */
    override fun shutdown() {
        // no-op: 资源由调用方管理
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
        is LLMMessage.AssistantToolCall, is LLMMessage.Tool ->
            throw IllegalArgumentException(
                "OpenAIClient.generate is text-only and does not support Function Calling messages " +
                "(${this::class.simpleName}). Use OpenAIFunctionCallingClient.generateWithTools instead."
            )
    }
}

package org.textrpg.application.adapter.llm

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.textrpg.application.adapter.llm.model.*
import org.textrpg.application.data.config.AppConfig

/**
 * 支持 Function Calling 的 OpenAI 兼容客户端
 *
 * 通过组合 [OpenAIClient] 实现 [FunctionCallingClient]，
 * 在文本生成基础上新增 tools/function_calling 支持。
 *
 * 兼容所有支持 OpenAI Function Calling 格式的 API（OpenAI、Azure、本地兼容服务等）。
 *
 * @param config 应用配置
 * @param httpClient Ktor HTTP 客户端
 * @param delegate 委托的基础 LLMClient（默认 OpenAIClient）
 */
class OpenAIFunctionCallingClient(
    private val config: AppConfig,
    private val httpClient: HttpClient,
    private val delegate: LLMClient = OpenAIClient(config, httpClient)
) : FunctionCallingClient, LLMClient by delegate {

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type

    /**
     * 带工具定义的文本生成
     *
     * 构建包含 tools 数组的 OpenAI 格式请求体，解析响应中的 tool_calls。
     */
    override suspend fun generateWithTools(
        messages: List<LLMMessage>,
        tools: List<ToolDefinition>
    ): Result<FunctionCallingResponse> {
        if (!config.llm.enabled) {
            return Result.failure(Exception("LLM is disabled"))
        }

        return try {
            val requestBody = JsonObject().apply {
                addProperty("model", config.llm.model)
                add("messages", buildMessagesArray(messages))
                add("tools", buildToolsArray(tools))
                addProperty("stream", false)
            }

            val response = httpClient.post(config.llm.apiUrl) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${config.llm.apiKey}")
                setBody(gson.toJson(requestBody))
            }

            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val parsed = parseResponse(body)
                Result.success(parsed)
            } else {
                Result.failure(Exception("LLM request failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 构建消息数组
     */
    private fun buildMessagesArray(messages: List<LLMMessage>): JsonArray {
        return JsonArray().apply {
            messages.forEach { msg ->
                add(JsonObject().apply {
                    when (msg) {
                        is LLMMessage.System -> {
                            addProperty("role", "system")
                            addProperty("content", msg.message)
                        }
                        is LLMMessage.Assistant -> {
                            addProperty("role", "assistant")
                            addProperty("content", msg.message)
                        }
                        is LLMMessage.User -> {
                            addProperty("role", "user")
                            addProperty("content", msg.message)
                        }
                    }
                })
            }
        }
    }

    /**
     * 构建工具定义数组（OpenAI tools 格式）
     *
     * OpenAI 格式：
     * ```json
     * {
     *   "type": "function",
     *   "function": {
     *     "name": "tool_name",
     *     "description": "tool description",
     *     "parameters": {
     *       "type": "object",
     *       "properties": { ... },
     *       "required": [ ... ]
     *     }
     *   }
     * }
     * ```
     */
    private fun buildToolsArray(tools: List<ToolDefinition>): JsonArray {
        return JsonArray().apply {
            tools.forEach { tool ->
                add(JsonObject().apply {
                    addProperty("type", "function")
                    add("function", JsonObject().apply {
                        addProperty("name", tool.name)
                        addProperty("description", tool.description)
                        add("parameters", buildParametersSchema(tool.parameters))
                    })
                })
            }
        }
    }

    /**
     * 构建参数 JSON Schema
     */
    private fun buildParametersSchema(parameters: Map<String, ToolParameter>): JsonObject {
        val properties = JsonObject()
        val required = JsonArray()

        parameters.forEach { (name, param) ->
            properties.add(name, JsonObject().apply {
                addProperty("type", param.type)
                if (param.description.isNotBlank()) {
                    addProperty("description", param.description)
                }
                if (param.enum != null) {
                    add("enum", JsonArray().apply {
                        param.enum.forEach { add(it) }
                    })
                }
            })
            if (param.required) {
                required.add(name)
            }
        }

        return JsonObject().apply {
            addProperty("type", "object")
            add("properties", properties)
            if (required.size() > 0) {
                add("required", required)
            }
        }
    }

    /**
     * 解析 LLM 响应
     *
     * 提取文本内容和 tool_calls。
     */
    private fun parseResponse(body: String): FunctionCallingResponse {
        val json = gson.fromJson(body, JsonObject::class.java)
        val choice = json.getAsJsonArray("choices")
            ?.firstOrNull()?.asJsonObject
            ?: return FunctionCallingResponse()

        val message = choice.getAsJsonObject("message")
            ?: return FunctionCallingResponse()

        val content = message.get("content")?.takeIf { !it.isJsonNull }?.asString

        val toolCalls = message.getAsJsonArray("tool_calls")?.map { tcElement ->
            val tc = tcElement.asJsonObject
            val function = tc.getAsJsonObject("function")
            val callId = tc.get("id")?.asString ?: ""
            val name = function.get("name")?.asString ?: ""
            val argsStr = function.get("arguments")?.asString ?: "{}"
            val arguments: Map<String, Any?> = try {
                gson.fromJson(argsStr, mapType) ?: emptyMap()
            } catch (_: Exception) {
                emptyMap()
            }
            ToolCall(id = callId, name = name, arguments = arguments)
        } ?: emptyList()

        return FunctionCallingResponse(content = content, toolCalls = toolCalls)
    }
}

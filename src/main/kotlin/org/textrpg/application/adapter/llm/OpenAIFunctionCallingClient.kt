package org.textrpg.application.adapter.llm

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
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
            return Result.failure(IllegalStateException("LLM is disabled"))
        }

        return runCatching {
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

            if (!response.status.isSuccess()) {
                error("LLM request failed: ${response.status}")
            }

            parseResponse(response.bodyAsText())
        }
    }

    /**
     * 构建消息数组
     *
     * 完整支持 OpenAI Chat Completions 协议的全部消息角色：
     * - `system` / `user` / `assistant`（普通对话）
     * - `assistant` + `tool_calls`（LLM 发起工具调用）
     * - `tool` + `tool_call_id`（工具结果回传）
     */
    private fun buildMessagesArray(messages: List<LLMMessage>): JsonArray {
        return JsonArray().apply {
            messages.forEach { add(buildMessageJson(it)) }
        }
    }

    private fun buildMessageJson(msg: LLMMessage): JsonObject = when (msg) {
        is LLMMessage.System -> JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", msg.message)
        }
        is LLMMessage.Assistant -> JsonObject().apply {
            addProperty("role", "assistant")
            addProperty("content", msg.message)
        }
        is LLMMessage.User -> JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", msg.message)
        }
        is LLMMessage.AssistantToolCall -> JsonObject().apply {
            addProperty("role", "assistant")
            // OpenAI 协议允许 content 为 null（工具调用专用消息）
            if (msg.content != null) addProperty("content", msg.content) else add("content", JsonNull.INSTANCE)
            add("tool_calls", buildToolCallsArray(msg.toolCalls))
        }
        is LLMMessage.Tool -> JsonObject().apply {
            addProperty("role", "tool")
            addProperty("tool_call_id", msg.toolCallId)
            addProperty("content", msg.content)
        }
    }

    /**
     * 构建 LLM 响应中的 tool_calls 数组
     *
     * 用于 [LLMMessage.AssistantToolCall] 序列化时，把每个 [ToolCall] 还原为
     * OpenAI 期望的 `{id, type, function: {name, arguments}}` 结构。
     * `arguments` 字段按协议是 JSON **字符串**而非对象。
     */
    private fun buildToolCallsArray(toolCalls: List<ToolCall>): JsonArray {
        return JsonArray().apply {
            toolCalls.forEach { tc ->
                add(JsonObject().apply {
                    addProperty("id", tc.id)
                    addProperty("type", "function")
                    add("function", JsonObject().apply {
                        addProperty("name", tc.name)
                        addProperty("arguments", gson.toJson(tc.arguments))
                    })
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
            val arguments: Map<String, Any?> = runCatching<Map<String, Any?>> {
                gson.fromJson(argsStr, mapType) ?: emptyMap()
            }.getOrDefault(emptyMap())
            ToolCall(id = callId, name = name, arguments = arguments)
        } ?: emptyList()

        return FunctionCallingResponse(content = content, toolCalls = toolCalls)
    }
}

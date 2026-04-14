package org.textrpg.application.adapter.llm.model

/**
 * 工具参数定义
 *
 * 描述工具函数的一个参数，遵循 OpenAI Function Calling 的 JSON Schema 格式。
 *
 * @property type 参数类型（"string" / "number" / "integer" / "boolean" / "array" / "object"）
 * @property description 参数描述
 * @property required 是否为必填参数
 * @property enum 可选的枚举值列表
 */
data class ToolParameter(
    val type: String = "string",
    val description: String = "",
    val required: Boolean = false,
    val enum: List<String>? = null
)

/**
 * 工具定义
 *
 * 描述一个可供 LLM 调用的工具函数，传入 Function Calling API。
 *
 * @property name 工具名称（唯一标识）
 * @property description 工具功能描述（LLM 据此判断何时调用）
 * @property parameters 参数定义映射（参数名 → 参数定义）
 */
data class ToolDefinition(
    val name: String,
    val description: String = "",
    val parameters: Map<String, ToolParameter> = emptyMap()
)

/**
 * LLM 返回的工具调用
 *
 * @property id 工具调用 ID（OpenAI 格式中的 call ID，用于多轮对话匹配）
 * @property name 被调用的工具名称
 * @property arguments 工具参数映射
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any?> = emptyMap()
)

/**
 * Function Calling 响应
 *
 * LLM 的响应可能包含文本内容、工具调用或两者兼有。
 *
 * @property content 文本回复内容（工具调用时可能为 null）
 * @property toolCalls 工具调用列表（无工具调用时为空列表）
 */
data class FunctionCallingResponse(
    val content: String? = null,
    val toolCalls: List<ToolCall> = emptyList()
) {
    /** 是否包含工具调用 */
    val hasToolCalls: Boolean get() = toolCalls.isNotEmpty()
}

package org.textrpg.application.adapter.llm.model

/**
 * LLM 消息
 *
 * 对应 OpenAI Chat Completions API 的 message 类型，覆盖普通对话与
 * Function Calling 多轮对话两种场景。
 *
 * **普通对话**：仅使用 [System] / [Assistant] / [User]。
 *
 * **Function Calling 多轮**：当 LLM 返回工具调用时，下一轮请求需要把上一轮
 * 的工具调用决策与执行结果完整回放到消息历史中：
 * 1. 用 [AssistantToolCall] 表示"LLM 上一轮决定调用了哪些工具"
 * 2. 为每个工具的执行结果追加一条 [Tool] 消息（通过 [Tool.toolCallId] 关联到具体 [ToolCall.id]）
 *
 * 直接用 [Assistant] / [User] 字符串拼接来"伪装"工具结果会让 LLM 在下一轮
 * 失去工具调用上下文，违反 OpenAI Function Calling 协议。
 */
sealed class LLMMessage {
    /** 系统提示词（如安全规则、场景设定） */
    data class System(val message: String) : LLMMessage()

    /** Assistant 的纯文本回复 */
    data class Assistant(val message: String) : LLMMessage()

    /** 用户输入 */
    data class User(val message: String) : LLMMessage()

    /**
     * Assistant 发起的工具调用消息（OpenAI 协议 `role=assistant` + `tool_calls`）
     *
     * 把 LLM 上一轮决定调用工具的事实回放到下一轮对话历史中，与配对的 [Tool]
     * 消息共同构成完整的工具调用上下文。
     *
     * @property content LLM 在发起工具调用时附带的文本（可能为 null，即 LLM 完全用工具表达意图）
     * @property toolCalls 本轮发起的工具调用列表
     */
    data class AssistantToolCall(
        val content: String?,
        val toolCalls: List<ToolCall>
    ) : LLMMessage()

    /**
     * 工具调用结果消息（OpenAI 协议 `role=tool` + `tool_call_id`）
     *
     * 把单个工具的执行结果回传给 LLM。必须通过 [toolCallId] 关联到对应的
     * [ToolCall.id]，LLM 才能正确归位多个并发工具调用的返回值。
     *
     * @property toolCallId 关联的 [ToolCall.id]
     * @property content 工具执行结果文本
     */
    data class Tool(val toolCallId: String, val content: String) : LLMMessage()
}

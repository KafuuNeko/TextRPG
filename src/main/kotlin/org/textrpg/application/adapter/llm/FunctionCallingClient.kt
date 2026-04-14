package org.textrpg.application.adapter.llm

import org.textrpg.application.adapter.llm.model.FunctionCallingResponse
import org.textrpg.application.adapter.llm.model.LLMMessage
import org.textrpg.application.adapter.llm.model.ToolDefinition

/**
 * 支持 Function Calling 的 LLM 客户端接口
 *
 * 在 [LLMClient] 的文本生成基础上，新增工具调用能力。
 * LLM 可以根据上下文选择调用预定义的工具函数，框架执行工具后可将结果回传给 LLM 进行多轮对话。
 *
 * @see LLMClient
 * @see ToolDefinition
 * @see FunctionCallingResponse
 */
interface FunctionCallingClient : LLMClient {

    /**
     * 带工具定义的文本生成
     *
     * 向 LLM 发送消息和可用工具列表，LLM 可选择生成文本或调用工具。
     *
     * @param messages 消息列表
     * @param tools 可用工具定义列表
     * @return 包含文本内容和/或工具调用的响应
     */
    suspend fun generateWithTools(
        messages: List<LLMMessage>,
        tools: List<ToolDefinition>
    ): Result<FunctionCallingResponse>
}

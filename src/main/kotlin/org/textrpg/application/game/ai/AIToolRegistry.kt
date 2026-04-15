package org.textrpg.application.game.ai

import org.textrpg.application.adapter.llm.model.ToolDefinition

/**
 * AI 工具运行时上下文
 *
 * 承载工具执行所需的"按调用注入"的依赖（如消息发送通道、当前会话引用等）。
 * 与 [AIToolHandler.definition] 的"静态描述"分离——后者用来告诉 LLM 工具的能力，
 * 本类用来让工具实际产生副作用。
 *
 * 典型场景：[BuiltinAITools.send_message] 收到 LLM 调用后，需要通过 [messageSink]
 * 把文本真正发送给玩家；纯查询/创建类工具（如 `create_node`）不需要 context，传 null 即可。
 *
 * @property messageSink 把 AI 想发的消息推送给目标（玩家/会话），null 表示退化为返回字符串
 */
data class AIToolContext(
    val messageSink: (suspend (String) -> Unit)? = null
)

/**
 * AI 工具处理器
 *
 * 将工具定义（描述给 LLM）和执行逻辑绑定在一起。
 *
 * @property definition 工具定义（传给 LLM 的描述信息）
 * @property handler 工具执行函数。第一个参数是 LLM 提供的 JSON 参数映射；
 *   第二个参数是可选的运行时上下文（[AIToolContext]），需要副作用的工具据此发送消息等。
 *   返回结果文本将作为 `role=tool` 消息回传给 LLM。
 */
data class AIToolHandler(
    val definition: ToolDefinition,
    val handler: suspend (args: Map<String, Any?>, context: AIToolContext?) -> String
)

/**
 * AI 工具注册表
 *
 * 框架级的工具管理中心。所有 AI 场景共享同一注册表，
 * 通过场景的 `allowedTools` 白名单过滤可用工具。
 *
 * 使用示例：
 * ```kotlin
 * registry.register("send_message", AIToolHandler(
 *     definition = ToolDefinition(name = "send_message", description = "发送消息", ...),
 *     handler = { args, ctx ->
 *         val text = args["message"]?.toString() ?: ""
 *         ctx?.messageSink?.invoke(text)
 *         text
 *     }
 * ))
 *
 * // 获取某场景的可用工具定义（传给 LLM）
 * val tools = registry.getToolDefinitions(listOf("send_message", "cast_skill"))
 *
 * // 执行工具（带权限检查）
 * val result = registry.executeTool("send_message", mapOf("message" to "hello"), allowedTools)
 * ```
 */
class AIToolRegistry {

    /** 工具注册表（工具名 → 处理器） */
    private val tools: MutableMap<String, AIToolHandler> = mutableMapOf()

    /**
     * 注册工具
     *
     * @param name 工具名称（唯一标识）
     * @param handler 工具处理器
     */
    fun register(name: String, handler: AIToolHandler) {
        tools[name] = handler
    }

    /**
     * 获取所有已注册的工具名
     */
    fun getAllToolNames(): Set<String> = tools.keys.toSet()

    /**
     * 按白名单过滤，获取工具定义列表
     *
     * 返回的列表传给 LLM 作为可用工具。
     *
     * @param allowedTools 允许的工具名列表
     * @return 过滤后的工具定义列表
     */
    fun getToolDefinitions(allowedTools: List<String>): List<ToolDefinition> {
        return allowedTools.mapNotNull { name ->
            tools[name]?.definition
        }
    }

    /**
     * 执行工具
     *
     * 先进行权限检查（工具是否在白名单中），然后执行。
     *
     * @param name 工具名称
     * @param arguments 工具参数
     * @param allowedTools 当前场景的工具白名单
     * @param context 运行时上下文（可选）；副作用类工具（如 send_message）需要它才能真正生效
     * @return 执行结果文本
     */
    suspend fun executeTool(
        name: String,
        arguments: Map<String, Any?>,
        allowedTools: List<String>,
        context: AIToolContext? = null
    ): String {
        // 权限检查
        if (name !in allowedTools) {
            return "Error: Tool '$name' is not allowed in this context. Allowed tools: $allowedTools"
        }

        val handler = tools[name]
            ?: return "Error: Tool '$name' is not registered"

        return runCatching { handler.handler(arguments, context) }
            .getOrElse { e -> "Error executing tool '$name': ${e.message}" }
    }
}

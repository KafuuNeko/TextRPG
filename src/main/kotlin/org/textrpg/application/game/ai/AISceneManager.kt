package org.textrpg.application.game.ai

import org.textrpg.application.adapter.llm.FunctionCallingClient
import org.textrpg.application.adapter.llm.model.FunctionCallingResponse
import org.textrpg.application.adapter.llm.model.LLMMessage
import org.textrpg.application.data.config.AIConfig

/**
 * AI 场景执行结果
 *
 * @property success 是否成功
 * @property content AI 生成的文本内容
 * @property toolResults 执行的工具操作日志
 * @property error 错误信息
 */
data class AISceneResult(
    val success: Boolean,
    val content: String? = null,
    val toolResults: List<String> = emptyList(),
    val error: String? = null
) {
    companion object {
        fun success(content: String?, toolResults: List<String> = emptyList()) =
            AISceneResult(true, content, toolResults)
        fun failed(error: String) = AISceneResult(false, error = error)
    }
}

/**
 * AI 场景管理器
 *
 * 统一管理所有 AI 场景的执行：构建 Prompt → 调用 LLM → 解析 Tool Calls → 执行工具 → 返回结果。
 *
 * 5 种内置场景：
 * 1. **NPC 对话**：基于 NPC Prompt + 玩家 Tag 生成对话
 * 2. **Boss AI 决策**：战斗中 Boss 回合的技能选择
 * 3. **动态节点生成**：边界节点探索时生成新区域
 * 4. **角色创建**：玩家自然语言描述 → 提取特征 → 分配属性
 * 5. **创世**：GM 输入世界观 → 生成初始内容
 *
 * 每个场景有独立的工具白名单（从 [AIConfig] 读取），超出白名单的工具调用被拒绝。
 *
 * @param client Function Calling 客户端
 * @param toolRegistry AI 工具注册表
 * @param tagManager 玩家 Tag 管理器
 * @param config AI 配置
 */
class AISceneManager(
    private val client: FunctionCallingClient,
    private val toolRegistry: AIToolRegistry,
    private val tagManager: PlayerTagManager,
    private val config: AIConfig
) {
    /** 最大工具调用轮次（防止无限循环） */
    private val maxToolRounds = 5

    /**
     * 执行 AI 场景
     *
     * 完整流程：
     * 1. 查找场景配置，获取工具白名单
     * 2. 构建 Prompt（安全层 + 场景模板 + 玩家 Tag + 用户输入）
     * 3. 调用 LLM（附带工具定义）
     * 4. 如果 LLM 返回 Tool Calls：执行工具 → 将结果回传 → 可能多轮
     * 5. 返回最终文本结果和工具执行日志
     *
     * @param sceneName 场景名称（对应 ai.yaml 中的配置 key）
     * @param playerId 玩家 ID（用于获取 Tag）
     * @param userPrompt 用户输入 / 场景上下文
     * @param extraContext 额外上下文信息（可选）
     * @param messageSink 可选消息通道。LLM 调用 `send_message` 工具时会立即推送到该通道；
     *   传 null 时退化为只把消息文本作为 tool result 回传给 LLM（适合 boss 决策等"AI 自言自语"场景）
     * @return 场景执行结果
     */
    suspend fun executeScene(
        sceneName: String,
        playerId: Long,
        userPrompt: String,
        extraContext: String? = null,
        messageSink: (suspend (String) -> Unit)? = null
    ): AISceneResult {
        // 1. 获取场景配置
        val sceneConfig = config.scenes[sceneName]
        val allowedTools = sceneConfig?.allowedTools ?: emptyList()
        val toolContext = AIToolContext(messageSink = messageSink)

        // 2. 构建消息列表
        val messages = mutableListOf<LLMMessage>()

        // 安全 Prompt
        val safetyPrompt = SafetyPromptProvider.getSafetyPrompt(config.safety)
        if (safetyPrompt.isNotBlank()) {
            messages.add(LLMMessage.System(safetyPrompt))
        }

        // 场景模板 Prompt
        if (sceneConfig?.promptTemplate != null) {
            messages.add(LLMMessage.System(sceneConfig.promptTemplate))
        }

        // 玩家 Tag 上下文
        val tagPrompt = tagManager.buildContextPrompt(playerId)
        if (tagPrompt.isNotBlank()) {
            messages.add(LLMMessage.System(tagPrompt))
        }

        // 额外上下文
        if (extraContext != null) {
            messages.add(LLMMessage.System(extraContext))
        }

        // 用户输入
        messages.add(LLMMessage.User(userPrompt))

        // 3. 获取工具定义
        val tools = toolRegistry.getToolDefinitions(allowedTools)

        // 4. 调用 LLM（可能多轮工具调用）
        val toolResultLog = mutableListOf<String>()
        var rounds = 0

        while (rounds < maxToolRounds) {
            rounds++

            val response = if (tools.isNotEmpty()) {
                client.generateWithTools(messages, tools)
            } else {
                // 无工具时退化为普通文本生成,把结果包装成 FunctionCallingResponse 统一处理
                client.generate(messages).map { FunctionCallingResponse(content = it) }
            }

            if (response.isFailure) {
                return AISceneResult.failed("LLM 请求失败：${response.exceptionOrNull()?.message}")
            }

            val fcResponse = response.getOrThrow()

            // 无工具调用 → 返回文本结果
            if (!fcResponse.hasToolCalls) {
                return AISceneResult.success(fcResponse.content, toolResultLog)
            }

            // 1. 把 LLM 本轮的工具调用决策完整回放到对话历史中
            //    （OpenAI Function Calling 协议要求 assistant 消息携带 tool_calls 字段）
            messages.add(
                LLMMessage.AssistantToolCall(
                    content = fcResponse.content,
                    toolCalls = fcResponse.toolCalls
                )
            )

            // 2. 顺序执行每个工具,把结果作为 role=tool 消息追加,并通过 tool_call_id 关联
            for (toolCall in fcResponse.toolCalls) {
                val result = toolRegistry.executeTool(toolCall.name, toolCall.arguments, allowedTools, toolContext)
                toolResultLog.add("${toolCall.name}(${toolCall.arguments}) → $result")
                messages.add(LLMMessage.Tool(toolCallId = toolCall.id, content = result))
            }
        }

        return AISceneResult.success(
            "（AI 达到最大工具调用轮次限制）",
            toolResultLog
        )
    }

    /**
     * NPC 对话场景
     *
     * @param playerId 玩家 ID
     * @param npcPrompt NPC 的角色 Prompt
     * @param playerMessage 玩家发送的对话消息
     * @param messageSink 可选消息通道——若提供，AI 通过 `send_message` 工具发出的消息会
     *   实时推送到该通道；否则只在最终返回值中体现
     * @return NPC 的回复文本
     */
    suspend fun npcDialogue(
        playerId: Long,
        npcPrompt: String,
        playerMessage: String,
        messageSink: (suspend (String) -> Unit)? = null
    ): String {
        val extraContext = "You are playing this NPC character. Respond in character.\n\nNPC Role:\n$npcPrompt"
        val result = executeScene("npc_dialogue", playerId, playerMessage, extraContext, messageSink)
        return result.content ?: result.error ?: "..."
    }

    /**
     * Boss AI 决策场景
     *
     * @param bossPrompt Boss 的角色 Prompt
     * @param battleSnapshot 当前战况快照文本
     * @param availableSkills 可用技能列表文本
     * @return AI 选择的技能 ID
     */
    suspend fun bossDecision(
        bossPrompt: String,
        battleSnapshot: String,
        availableSkills: String
    ): String {
        val prompt = """$bossPrompt

Current battle state:
$battleSnapshot

Available skills:
$availableSkills

Choose one skill to use. Respond with just the skill ID."""

        val result = executeScene("boss_combat", 0L, prompt)
        return result.content?.trim() ?: ""
    }

    /**
     * 动态节点生成场景
     *
     * @param playerId 玩家 ID
     * @param currentNodeInfo 当前节点信息
     * @param worldContext 世界观设定
     * @return AI 生成的描述文本（实际节点创建通过 Tool Call 完成）
     */
    suspend fun generateNode(
        playerId: Long,
        currentNodeInfo: String,
        worldContext: String
    ): AISceneResult {
        val prompt = """The player is exploring from this location:
$currentNodeInfo

World setting:
$worldContext

Generate a new area connected to this location. Use the create_node tool to create it."""

        return executeScene("world_generation", playerId, prompt)
    }
}

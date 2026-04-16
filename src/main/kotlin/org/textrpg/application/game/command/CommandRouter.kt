package org.textrpg.application.game.command

import org.textrpg.application.data.config.CommandConfig
import org.textrpg.application.domain.model.CommandDefinition

/**
 * 指令路由结果（密封类）
 *
 * 表示指令处理管道的各种终态，由调用方根据结果类型做后续处理。
 */
sealed class CommandResult {
    /** 消息不是指令（无前缀匹配），且不在会话中 */
    data object NotCommand : CommandResult()

    /** 指令名未匹配到任何定义 */
    data class UnknownCommand(val input: String) : CommandResult()

    /** 前置条件校验失败 */
    data class RequireFailed(val message: String) : CommandResult()

    /** 指令执行成功 */
    data class Success(val response: String? = null) : CommandResult()

    /** 指令执行出错 */
    data class Error(val message: String) : CommandResult()

    /** 消息被活跃会话截获处理 */
    data class SessionHandled(val response: String? = null) : CommandResult()
}

/**
 * 指令路由器
 *
 * 消息处理的中枢，负责完整的指令生命周期：
 *
 * ```
 * 玩家消息
 *   → 会话截获检查（有活跃会话时交由会话处理）
 *   → 前缀匹配（判断是否为指令）
 *   → 指令解析（名称 + 参数分割）
 *   → 指令查找（名称 / 别名匹配）
 *   → Requires 校验
 *   → Cost 扣除
 *   → Handler 路由执行
 * ```
 *
 * @param config 指令配置（前缀 + 指令定义）
 * @param handlerRegistry 处理器注册表
 * @param sessionManager 会话管理器
 */
class CommandRouter(
    private val config: CommandConfig,
    private val handlerRegistry: CommandHandlerRegistry,
    private val sessionManager: SessionManager
) {
    companion object {
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }
    /**
     * 指令查找索引：合并 key 和 aliases，映射到 CommandDefinition
     *
     * 初始化时构建，支持 O(1) 查找。
     */
    private val commandIndex: Map<String, CommandDefinition> = buildIndex()

    /**
     * 处理玩家消息
     *
     * 完整流程：会话截获 → 前缀匹配 → 解析 → 查找 → 校验 → 扣费 → 执行。
     *
     * @param rawMessage 玩家发送的原始消息文本
     * @param context 执行上下文
     * @return 路由结果
     */
    suspend fun processMessage(rawMessage: String, context: CommandContext): CommandResult {
        val message = rawMessage.trim()

        // 1. 会话截获：如果玩家在活跃会话中，消息交由会话处理
        val session = sessionManager.getSession(context.playerId)
        if (session != null) {
            return try {
                val response = session.handleMessage(message)
                CommandResult.SessionHandled(response)
            } catch (e: Exception) {
                CommandResult.Error("会话处理异常：${e.message}")
            }
        }

        // 2. 前缀匹配
        if (!message.startsWith(config.commandPrefix)) {
            return CommandResult.NotCommand
        }

        // 3. 解析指令名和参数
        val content = message.removePrefix(config.commandPrefix)
        if (content.isBlank()) return CommandResult.NotCommand
        val parts = content.split(WHITESPACE_REGEX)
        val commandName = parts[0]
        val args = parts.drop(1)

        // 4. 查找指令定义（按名称和别名）
        val command = commandIndex[commandName]
            ?: return CommandResult.UnknownCommand(commandName)

        // 5. Requires 校验
        val requireResult = RequiresChecker.check(command.requires, context)
        if (!requireResult.passed) {
            return CommandResult.RequireFailed(requireResult.message ?: "条件不满足。")
        }

        // 6. Cost 扣除（校验通过后、执行前扣除）
        val costCheckResult = checkAndDeductCost(command, context)
        if (costCheckResult != null) {
            return CommandResult.RequireFailed(costCheckResult)
        }

        // 7. 路由到 Handler 执行
        val handler = handlerRegistry.resolve(command.handler)
            ?: return CommandResult.Error("处理器未注册：${command.handler}")

        return try {
            val response = handler.execute(args, context)
            CommandResult.Success(response)
        } catch (e: Exception) {
            CommandResult.Error("指令执行异常：${e.message}")
        }
    }

    /**
     * 检查并扣除指令消耗
     *
     * 先检查所有消耗是否足够，全部满足后才扣除（原子性保证）。
     *
     * @return 失败原因文本，null 表示扣除成功
     */
    private fun checkAndDeductCost(command: CommandDefinition, context: CommandContext): String? {
        if (command.cost.isEmpty()) return null

        // 先检查所有消耗是否满足
        for ((attrKey, amount) in command.cost) {
            val current = context.getAttributeValue(attrKey) ?: 0.0
            if (current < amount) {
                return "${attrKey} 不足（需要 $amount，当前 $current）。"
            }
        }

        // 全部满足后统一扣除
        for ((attrKey, amount) in command.cost) {
            context.modifyAttribute(attrKey, -amount)
        }

        return null
    }

    /**
     * 构建指令查找索引
     *
     * 将每个指令的 key 和所有 aliases 都映射到同一个 CommandDefinition，
     * 实现 O(1) 的指令查找。
     */
    private fun buildIndex(): Map<String, CommandDefinition> {
        val index = mutableMapOf<String, CommandDefinition>()
        config.commands.forEach { (key, def) ->
            index[key] = def
            def.aliases.forEach { alias ->
                index[alias] = def
            }
        }
        return index
    }
}

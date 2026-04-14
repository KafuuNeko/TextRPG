package org.textrpg.application.game.command

/**
 * 指令处理器接口
 *
 * 每个处理器负责一类指令的具体执行逻辑。
 * 内置处理器由引擎提供，脚本处理器通过 Kotlin Scripting 动态加载。
 *
 * 处理器通过 [CommandHandlerRegistry] 注册，由 [CommandRouter] 在指令匹配后调用。
 */
fun interface CommandHandler {
    /**
     * 执行指令
     *
     * @param args 指令参数列表（已去除指令名和前缀）
     * @param context 执行上下文
     * @return 返回给玩家的消息文本，null 表示无需回复
     */
    suspend fun execute(args: List<String>, context: CommandContext): String?
}

/**
 * 指令处理器注册表
 *
 * 管理所有可用的指令处理器。处理器通过 handler 标识符查找：
 * - `"builtin:xxx"` → 查找名为 `xxx` 的内置处理器
 * - `"script:xxx.kts"` → 加载并执行脚本（由游戏层实现脚本加载逻辑）
 *
 * 使用示例：
 * ```kotlin
 * val registry = CommandHandlerRegistry()
 * registry.registerBuiltin("status") { args, ctx ->
 *     "你的生命值：${ctx.getAttributeValue("current_hp")}"
 * }
 * registry.registerBuiltin("help") { args, ctx ->
 *     "可用指令：/status, /move, /help"
 * }
 * ```
 */
class CommandHandlerRegistry {

    /** 内置处理器映射（名称 -> 处理器） */
    private val builtinHandlers: MutableMap<String, CommandHandler> = mutableMapOf()

    /** 脚本处理器工厂（可选，由游戏层设置） */
    var scriptHandlerFactory: ScriptHandlerFactory? = null

    /**
     * 注册内置处理器
     *
     * @param name 处理器名称（对应 handler 字段中 `builtin:` 后面的部分）
     * @param handler 处理器实现
     */
    fun registerBuiltin(name: String, handler: CommandHandler) {
        builtinHandlers[name] = handler
    }

    /**
     * 根据 handler 标识符查找处理器
     *
     * @param handlerId 处理器标识符（如 `"builtin:status"` 或 `"script:gather.kts"`）
     * @return 处理器实例，找不到时返回 null
     */
    fun resolve(handlerId: String): CommandHandler? {
        val parts = handlerId.split(":", limit = 2)
        if (parts.size != 2) return null

        return when (parts[0]) {
            "builtin" -> builtinHandlers[parts[1]]
            "script" -> scriptHandlerFactory?.create(parts[1])
            else -> null
        }
    }
}

/**
 * 脚本处理器工厂接口
 *
 * 由游戏层实现，负责加载 .kts 脚本并包装为 [CommandHandler]。
 */
fun interface ScriptHandlerFactory {
    /**
     * 创建脚本处理器
     *
     * @param scriptPath 脚本文件路径（相对于脚本根目录）
     * @return 脚本处理器实例
     */
    fun create(scriptPath: String): CommandHandler
}

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
 * **三种注册路径**：
 *
 * | 路径 | 创建方式 | 适用场景 |
 * |------|----------|----------|
 * | [registerBuiltin]（闭包） | lambda / SAM | 简单指令、依赖少 |
 * | [registerAll]（类 + 批量） | [NamedCommandHandler] 实例 | 依赖多、需要 Koin 注入与单测 |
 * | [scriptHandlerFactory] | `.kts` 动态编译 | 脚本热更新、运行时扩展 |
 *
 * 用法示例：
 * ```kotlin
 * registry.registerBuiltin("ping") { _, _ -> "pong" }
 * registry.registerAll(getKoin().getAll<NamedCommandHandler>())
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
     * @param handler 处理器实现（可以是 lambda 或实现 [CommandHandler] 的类）
     */
    fun registerBuiltin(name: String, handler: CommandHandler) {
        builtinHandlers[name] = handler
    }

    /**
     * 批量注册 [NamedCommandHandler] 类实例
     *
     * 与 [scriptHandlerFactory] 通道对称：脚本通道按需创建，本通道一次性装载。
     * 典型用法是从 Koin `getAll<NamedCommandHandler>()` 拿到所有实例后统一注册。
     *
     * 同名 Handler 重复注册时**后者覆盖前者**（日志由调用方自行决定）。
     *
     * @param handlers 要注册的 Handler 列表
     */
    fun registerAll(handlers: List<NamedCommandHandler>) {
        handlers.forEach { builtinHandlers[it.name] = it }
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

package org.textrpg.application.game.command

/**
 * 自带注册名的指令处理器接口
 *
 * [CommandHandler] 是 `fun interface`，闭包/lambda 注册适合**简单、依赖少**的指令：
 * ```kotlin
 * registry.registerBuiltin("ping") { _, _ -> "pong" }
 * ```
 *
 * 当处理器依赖 ≥ 3 个、逻辑 ≥ 30 行，或希望走 Koin 构造器注入 + 单测时，
 * 推荐实现本接口将处理器独立为类，并通过 Koin `singleOf(::XxxHandler) bind NamedCommandHandler::class`
 * 注册，由 [CommandHandlerRegistry.registerAll] 批量装载。
 *
 * **设计说明**：本接口不能做成 `fun interface`——含 [name] 属性，SAM 转换不支持多成员接口。
 *
 * @see CommandHandler 基础 SAM 接口
 * @see CommandHandlerRegistry.registerAll 批量注册入口
 */
interface NamedCommandHandler : CommandHandler {
    /**
     * 处理器名称
     *
     * 对应 `commands.yaml` 里 `handler: "builtin:xxx"` 的 `xxx` 部分。
     * 注册到 [CommandHandlerRegistry] 时作为 key 使用。
     */
    val name: String
}

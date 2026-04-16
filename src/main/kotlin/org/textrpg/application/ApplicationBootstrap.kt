package org.textrpg.application

import io.github.oshai.kotlinlogging.KLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.textrpg.application.adapter.onebot.OneBotAdapter
import org.textrpg.application.game.command.CommandHandlerRegistry
import org.textrpg.application.game.command.NamedCommandHandler

/**
 * 应用启动编排骨架
 *
 * 把 `Application.kt` 从"启动 Koin + 消息循环 + 手动 wire"的混合职责中解放出来。
 * 启动流程分三个钩子，范例层通过继承覆盖即可：
 *
 * 1. [onBeforeStart] — 启动前一次性初始化（如数据播种）
 * 2. [registerHandlers] — 从 Koin 批量取 [NamedCommandHandler] 注册到 [CommandHandlerRegistry]
 * 3. [startLoop] — 启动外部消息循环（默认连接 [OneBotAdapter]）
 *
 * **使用方式**：
 * - 框架默认：通过 Koin `get<ApplicationBootstrap>()` 拿到本类实例，调用 [start]
 * - 范例扩展：继承本类并在范例 Koin 模块中 `single<ApplicationBootstrap>(override = true) { ... }`
 *
 * @param adapter OneBot 消息适配器（消息循环入口）
 * @param registry 指令处理器注册表
 */
open class ApplicationBootstrap(
    protected val adapter: OneBotAdapter,
    protected val registry: CommandHandlerRegistry
) : KoinComponent {
    private val log by lazy { get<KLogger>() }

    /**
     * 主启动入口：按序执行三个钩子。
     *
     * 异常收敛只发生在 [startLoop] 内部，[onBeforeStart] / [registerHandlers] 抛出的异常
     * 会冒泡到 `main`。范例层有特殊容错需求时自行覆盖钩子并 try-catch。
     */
    suspend fun start() {
        onBeforeStart()
        registerHandlers()
        startLoop()
    }

    /**
     * 启动前钩子（默认空实现）
     *
     * 典型用途：调用范例层的数据播种器（如 `ItemSeeder.seed()`）。
     */
    protected open suspend fun onBeforeStart() {
        // 默认空实现
    }

    /**
     * 注册 Handler 钩子（默认实现：从 Koin 批量拉所有 [NamedCommandHandler]）
     *
     * **范例层注册要求**：必须用 `bind NamedCommandHandler::class` 显式绑定，否则
     * `getKoin().getAll<NamedCommandHandler>()` 拿不到。示例：
     * ```kotlin
     * singleOf(::MoveCommandHandler) bind NamedCommandHandler::class
     * ```
     *
     * 范例追加闭包式 Handler 时可覆盖本方法：先 `super.registerHandlers()` 保留默认批量注册，
     * 再调用 [CommandHandlerRegistry.registerBuiltin]。
     */
    protected open suspend fun registerHandlers() {
        val handlers: List<NamedCommandHandler> = getKoin().getAll()
        registry.registerAll(handlers)
    }

    /**
     * 启动消息循环钩子（默认实现：注册 debug 监听器 + 连接 OneBot 适配器）
     *
     * 默认监听器仅把消息打印到 stdout，方便 develop 分支冒烟测试。
     * 范例层通常会完全覆盖本方法，挂自己的 [CommandRouter] 到 [OneBotAdapter.registerMessageListener]。
     * 裸 try-catch 收敛为 [runCatching]，对齐规范 §8.3。
     */
    protected open suspend fun startLoop() {
        adapter.registerMessageListener { event ->
            log.info { "收到消息 from ${event.userId}: ${event.getPlainText()}" }
            if (event.isGroup) {
                log.info { "  群组: ${event.groupId}" }
            }
        }

        runCatching { adapter.connect() }
            .onFailure { log.error(it) { "Connection failed" } }
    }
}

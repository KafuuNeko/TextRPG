package org.textrpg.application

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.textrpg.application.adapter.llm.LLMClient
import org.textrpg.application.adapter.llm.OpenAIClient
import org.textrpg.application.adapter.onebot.OneBotAdapter
import org.textrpg.application.adapter.onebot.OneBotConfig as OneBotAdapterConfig
import org.textrpg.application.data.config.AppConfig
import org.textrpg.application.data.config.ConfigLoader
import org.textrpg.application.data.configModule
import org.textrpg.application.data.databaseModule
import org.textrpg.application.data.repositoryModule
import org.textrpg.application.game.ai.aiModule
import org.textrpg.application.game.gameModule
import org.textrpg.application.utils.script.KotlinScriptRunner

/**
 * 应用入口
 *
 * 职责限定：
 * 1. 定义 `appModule` — 基础设施单例（HttpClient / AppConfig / OneBotAdapter / LLMClient /
 *    KotlinScriptRunner / CoroutineScope / ApplicationBootstrap）
 * 2. 启动 Koin 并注册 6 个框架层模块
 * 3. 委托给 [ApplicationBootstrap] 执行启动流程
 *
 * 新增指令 / Manager / AI 场景应修改对应层的 Koin 模块，**不要扩张本文件**。
 */
object Application : KoinComponent {
    private val appModule = module {
        single<HttpClient> {
            HttpClient(CIO) {
                install(ContentNegotiation) {
                    Json { ignoreUnknownKeys = true; isLenient = true }
                }
                install(WebSockets)
                install(HttpTimeout) {
                    requestTimeoutMillis = 30_000
                    connectTimeoutMillis = 10_000
                }
            }
        }
        single<AppConfig> { ConfigLoader.loadOrDefault() }
        singleOf(::KotlinScriptRunner)
        single<OneBotAdapter> {
            val appConfig: AppConfig = get()
            OneBotAdapter(
                config = OneBotAdapterConfig(
                    websocketUrl = appConfig.bot.websocketUrl,
                    httpUrl = appConfig.bot.httpUrl,
                    accessToken = appConfig.bot.accessToken.takeIf { it.isNotBlank() }
                ),
                httpClient = get()
            )
        }
        single<LLMClient> { OpenAIClient(get(), get()) }
        // 游戏协程作用域：承载战斗会话、AI 异步决策等挂起任务
        single<CoroutineScope> { CoroutineScope(Dispatchers.Default + SupervisorJob()) }
        // 启动编排器：范例层可通过 override = true 替换为定制子类
        singleOf(::ApplicationBootstrap)
    }

    init {
        startKoin {
            modules(appModule, configModule, databaseModule, repositoryModule, gameModule, aiModule)
        }
    }

    suspend fun run() {
        val bootstrap: ApplicationBootstrap = get()
        bootstrap.start()
    }
}

fun main() {
    runBlocking { Application.run() }
}

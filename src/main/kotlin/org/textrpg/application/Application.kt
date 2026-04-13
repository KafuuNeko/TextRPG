package org.textrpg.application

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.textrpg.application.config.AppConfig
import org.textrpg.application.config.ConfigLoader
import org.textrpg.application.llm.LLMClient
import org.textrpg.application.llm.OpenAIClient
import org.textrpg.application.script.KotlinScriptRunner
import org.textrpg.onebot.OneBotAdapter
import org.textrpg.onebot.OneBotConfig as OneBotAdapterConfig
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

object Application : KoinComponent {
    private val appModules = module {
        single<HttpClient> {
            HttpClient(CIO) {
                install(ContentNegotiation) {
                    Json { ignoreUnknownKeys = true; isLenient = true }
                }
                install(WebSockets)
            }
        }

        single<AppConfig> {
            ConfigLoader.loadOrDefault()
        }

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
    }

    init {
        startKoin { modules(appModules) }
    }

    suspend fun loop() {
        val adapter: OneBotAdapter = get()

        // 注册消息监听器
        adapter.registerMessageListener { event ->
            println("收到消息 from ${event.userId}: ${event.getPlainText()}")
            if (event.isGroup) {
                println("  群组: ${event.groupId}")
            }
        }

        adapter.connect()
    }
}

fun main() {
    runBlocking { Application.loop() }
}

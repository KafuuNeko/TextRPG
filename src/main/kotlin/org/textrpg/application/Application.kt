package org.textrpg.application

import com.google.gson.Gson
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.textrpg.application.adapter.llm.LLMClient
import org.textrpg.application.adapter.llm.OpenAIClient
import org.textrpg.application.data.config.AppConfig
import org.textrpg.application.data.config.ConfigLoader
import org.textrpg.application.data.databaseModule
import org.textrpg.application.data.repositoryModule
import org.textrpg.application.data.staticDataModule
import org.textrpg.application.data.managerModule
import org.textrpg.application.utils.script.KotlinScriptRunner
import org.textrpg.application.adapter.onebot.OneBotAdapter
import org.textrpg.application.adapter.onebot.OneBotConfig as OneBotAdapterConfig
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.yaml.snakeyaml.Yaml

object Application : KoinComponent {
    private val appModules = module {
        single<Gson> { Gson() }
        single<Yaml> { Yaml() }

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
                mConfig = OneBotAdapterConfig(
                    websocketUrl = appConfig.bot.websocketUrl,
                    httpUrl = appConfig.bot.httpUrl,
                    accessToken = appConfig.bot.accessToken.takeIf { it.isNotBlank() }
                ),
                mHttpClient = get()
            )
        }

        single<LLMClient> { OpenAIClient(get(), get()) }
    }

    init {
        startKoin { modules(appModules, databaseModule, repositoryModule, staticDataModule, managerModule) }
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

        try {
            adapter.connect()
        } catch (e: Exception) {
            println("Connection failed: ${e.localizedMessage}")
        }

    }
}

fun main() {
    runBlocking { Application.loop() }
}

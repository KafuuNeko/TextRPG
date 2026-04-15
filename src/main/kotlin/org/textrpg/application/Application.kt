package org.textrpg.application

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.*
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
import org.textrpg.application.data.config.*
import org.textrpg.application.data.databaseModule
import org.textrpg.application.data.repository.PlayerRepository
import org.textrpg.application.data.repositoryModule
import org.textrpg.application.game.command.*
import org.textrpg.application.game.player.PlayerContext
import org.textrpg.application.game.player.PlayerManager
import org.textrpg.application.utils.script.KotlinScriptRunner

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
    }

    init {
        startKoin { modules(appModules, databaseModule, repositoryModule) }
    }

    /** 游戏逻辑协程作用域 */
    private val gameScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    suspend fun loop() {
        val adapter: OneBotAdapter = get()
        val playerRepository: PlayerRepository = get()

        // ========== 加载配置 ==========
        val attributeDefinitions = AttributeConfigLoader.load()
        val commandConfig = CommandConfigLoader.load()
        println("已加载 ${attributeDefinitions.size} 个属性定义，${commandConfig.commands.size} 个指令定义")

        // ========== 创建管理器 ==========
        val sessionManager = SessionManager()
        val handlerRegistry = CommandHandlerRegistry()
        val commandRouter = CommandRouter(commandConfig, handlerRegistry, sessionManager)

        val playerManager = PlayerManager(
            playerRepository = playerRepository,
            attributeDefinitions = attributeDefinitions,
            buffDefinitions = emptyMap(), // Phase 3 接入
            sessionManager = sessionManager,
            mapManager = null              // Phase 2 接入
        )

        // ========== 注册指令处理器 ==========
        registerHandlers(handlerRegistry, playerManager)

        // ========== OneBot 消息监听 ==========
        adapter.registerMessageListener { event ->
            val rawText = event.getPlainText().trim()
            if (rawText.isBlank()) return@registerMessageListener

            val bindAccount = event.userId

            // 构建消息回复函数
            val replier: suspend (String) -> Unit = { msg ->
                val gid = event.groupId
                if (event.isGroup && gid != null) {
                    adapter.sendGroupMessage(gid, msg)
                } else {
                    adapter.sendPrivateMessage(event.userId, msg)
                }
            }

            // 在游戏协程中处理指令
            gameScope.launch {
                try {
                    val context = playerManager.getContext(bindAccount, replier)
                    val result = commandRouter.processMessage(rawText, context)

                    when (result) {
                        is CommandResult.Success -> result.response?.let { replier(it) }
                        is CommandResult.RequireFailed -> replier(result.message)
                        is CommandResult.UnknownCommand -> { /* 未知指令静默忽略 */ }
                        is CommandResult.Error -> replier("系统异常：${result.message}")
                        is CommandResult.SessionHandled -> result.response?.let { replier(it) }
                        is CommandResult.NotCommand -> { /* 非指令消息静默忽略 */ }
                    }
                } catch (e: Exception) {
                    println("消息处理异常 [${event.userId}]: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        println("TextRPG 启动完成，等待连接...")
        try {
            adapter.connect()
        } catch (e: Exception) {
            println("连接失败: ${e.localizedMessage}")
        }
    }

    /**
     * 注册所有内置指令处理器
     */
    private fun registerHandlers(
        registry: CommandHandlerRegistry,
        playerManager: PlayerManager
    ) {
        // /注册 <角色名>
        registry.registerBuiltin("register", CommandHandler { args, context ->
            val ctx = context as PlayerContext

            if (ctx.isRegistered) {
                return@CommandHandler "你已经注册过角色了。使用 /状态 查看属性。"
            }
            if (args.isEmpty()) {
                return@CommandHandler "请输入角色名！\n用法：/注册 <角色名>"
            }

            val name = args.joinToString(" ")
            if (name.length > 16) {
                return@CommandHandler "角色名过长，最多 16 个字符。"
            }

            if (playerManager.isNameTaken(name)) {
                return@CommandHandler "角色名「$name」已被占用，换一个试试？"
            }

            playerManager.registerPlayer(name, ctx.bindAccount)
            buildString {
                appendLine("━━━ 角色创建成功 ━━━")
                appendLine("名称：$name")
                appendLine("等级：Lv.1")
                appendLine()
                appendLine("输入 /状态 查看完整属性")
            }.trimEnd()
        })

        // /状态
        registry.registerBuiltin("status", CommandHandler { _, context ->
            val ctx = context as PlayerContext
            val attrs = ctx.attributeContainer
                ?: return@CommandHandler "属性数据异常，请联系管理员。"

            buildString {
                appendLine("━━━━━ 角色状态 ━━━━━")

                // 基础属性
                val level = attrs.getValue("level").toInt()
                val exp = attrs.getValue("exp").toInt()
                appendLine("等级：Lv.$level  经验：$exp")
                appendLine("金币：${attrs.getValue("gold").toInt()}")
                appendLine()

                // 生命 / 魔力
                val hp = attrs.getValue("current_hp").toInt()
                val maxHp = attrs.getValue("max_hp").toInt()
                val mp = attrs.getValue("current_mp").toInt()
                val maxMp = attrs.getValue("max_mp").toInt()
                appendLine("HP: $hp / $maxHp")
                appendLine("MP: $mp / $maxMp")
                appendLine()

                // 基础四维
                appendLine("力量: ${attrs.getValue("strength").toInt()}  " +
                           "敏捷: ${attrs.getValue("agility").toInt()}")
                appendLine("体质: ${attrs.getValue("constitution").toInt()}  " +
                           "智力: ${attrs.getValue("intelligence").toInt()}")
                appendLine()

                // 战斗属性
                appendLine("物攻: ${attrs.getValue("physical_attack").toInt()}  " +
                           "魔攻: ${attrs.getValue("magic_attack").toInt()}")
                appendLine("防御: ${attrs.getValue("defense").toInt()}")
            }.trimEnd()
        })
    }
}

fun main() {
    runBlocking { Application.loop() }
}

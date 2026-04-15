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
import org.textrpg.application.data.repository.ItemRepository
import org.textrpg.application.data.repository.PlayerRepository
import org.textrpg.application.data.repositoryModule
import org.textrpg.application.domain.model.EquipmentSlot
import org.textrpg.application.domain.model.ItemType
import org.textrpg.application.game.command.*
import org.textrpg.application.game.equipment.EquipmentService
import org.textrpg.application.game.inventory.InventoryService
import org.textrpg.application.game.inventory.ItemStatsParser
import org.textrpg.application.game.map.MapManager
import org.textrpg.application.game.player.ItemSeeder
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
        val itemRepository: ItemRepository = get()

        // ========== 加载配置 ==========
        val attributeDefinitions = AttributeConfigLoader.load()
        val commandConfig = CommandConfigLoader.load()
        val mapConfig = MapConfigLoader.load()
        val inventoryConfig = InventoryConfigLoader.load()

        println("已加载 ${attributeDefinitions.size} 个属性，${commandConfig.commands.size} 个指令，${mapConfig.nodes.size} 个地图节点")

        // ========== 播种物品模板 ==========
        val seeded = ItemSeeder.seed(itemRepository)
        if (seeded > 0) println("已播种 $seeded 个物品模板")

        // ========== 创建管理器 ==========
        val sessionManager = SessionManager()
        val handlerRegistry = CommandHandlerRegistry()
        val commandRouter = CommandRouter(commandConfig, handlerRegistry, sessionManager)
        val mapManager = MapManager(mapConfig)
        val inventoryService = InventoryService(itemRepository, inventoryConfig)
        val equipmentService = EquipmentService(itemRepository, inventoryService)

        val playerManager = PlayerManager(
            playerRepository = playerRepository,
            attributeDefinitions = attributeDefinitions,
            buffDefinitions = emptyMap(),
            sessionManager = sessionManager,
            mapManager = mapManager,
            inventoryService = inventoryService
        )

        // ========== 注册指令处理器 ==========
        registerHandlers(
            registry = handlerRegistry,
            playerManager = playerManager,
            mapManager = mapManager,
            itemRepository = itemRepository,
            equipmentService = equipmentService
        )

        // ========== OneBot 消息监听 ==========
        adapter.registerMessageListener { event ->
            val rawText = event.getPlainText().trim()
            if (rawText.isBlank()) return@registerMessageListener

            val bindAccount = event.userId

            val replier: suspend (String) -> Unit = { msg ->
                val gid = event.groupId
                if (event.isGroup && gid != null) {
                    adapter.sendGroupMessage(gid, msg)
                } else {
                    adapter.sendPrivateMessage(event.userId, msg)
                }
            }

            gameScope.launch {
                try {
                    val context = playerManager.getContext(bindAccount, replier)
                    val result = commandRouter.processMessage(rawText, context)

                    when (result) {
                        is CommandResult.Success -> result.response?.let { replier(it) }
                        is CommandResult.RequireFailed -> replier(result.message)
                        is CommandResult.UnknownCommand -> {}
                        is CommandResult.Error -> replier("系统异常：${result.message}")
                        is CommandResult.SessionHandled -> result.response?.let { replier(it) }
                        is CommandResult.NotCommand -> {}
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
        playerManager: PlayerManager,
        mapManager: MapManager,
        itemRepository: ItemRepository,
        equipmentService: EquipmentService
    ) {
        // ==================== /注册 ====================
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

            val player = playerManager.registerPlayer(name, ctx.bindAccount)

            // 赠送初始物品：木剑 x1 + 治疗药水 x3
            val templates = itemRepository.findAllTemplates()
            val startingItems = mutableMapOf<Int, Int>()
            templates.find { it.name == "木剑" }?.let { startingItems[it.id] = 1 }
            templates.find { it.name == "治疗药水" }?.let { startingItems[it.id] = 3 }
            if (startingItems.isNotEmpty()) {
                playerManager.giveStartingItems(player.id, startingItems)
            }

            buildString {
                appendLine("━━━ 角色创建成功 ━━━")
                appendLine("名称：$name")
                appendLine("等级：Lv.1")
                appendLine("位置：村庄广场")
                appendLine()
                appendLine("获得：木剑 x1、治疗药水 x3")
                appendLine()
                appendLine("输入 /状态 查看属性")
                appendLine("输入 /移动 <地名> 探索世界")
            }.trimEnd()
        })

        // ==================== /状态 ====================
        registry.registerBuiltin("status", CommandHandler { _, context ->
            val ctx = context as PlayerContext
            val attrs = ctx.attributeContainer
                ?: return@CommandHandler "属性数据异常，请联系管理员。"

            val locationId = mapManager.getPlayerLocation(ctx.playerId)
            val locationName = locationId?.let { mapManager.getNode(it)?.displayName } ?: "未知"

            buildString {
                appendLine("━━━━━ 角色状态 ━━━━━")

                val level = attrs.getValue("level").toInt()
                val exp = attrs.getValue("exp").toInt()
                appendLine("等级：Lv.$level  经验：$exp")
                appendLine("金币：${attrs.getValue("gold").toInt()}")
                appendLine("位置：$locationName")
                appendLine()

                val hp = attrs.getValue("current_hp").toInt()
                val maxHp = attrs.getValue("max_hp").toInt()
                val mp = attrs.getValue("current_mp").toInt()
                val maxMp = attrs.getValue("max_mp").toInt()
                appendLine("HP: $hp / $maxHp")
                appendLine("MP: $mp / $maxMp")
                appendLine()

                appendLine("力量: ${attrs.getValue("strength").toInt()}  " +
                           "敏捷: ${attrs.getValue("agility").toInt()}")
                appendLine("体质: ${attrs.getValue("constitution").toInt()}  " +
                           "智力: ${attrs.getValue("intelligence").toInt()}")
                appendLine()

                appendLine("物攻: ${attrs.getValue("physical_attack").toInt()}  " +
                           "魔攻: ${attrs.getValue("magic_attack").toInt()}")
                appendLine("防御: ${attrs.getValue("defense").toInt()}")
            }.trimEnd()
        })

        // ==================== /移动 ====================
        registry.registerBuiltin("move", CommandHandler { args, context ->
            val ctx = context as PlayerContext

            if (args.isEmpty()) {
                // 无参数时显示当前位置和可用方向
                val locationId = mapManager.getPlayerLocation(ctx.playerId)
                    ?: return@CommandHandler "位置未初始化，请联系管理员。"
                return@CommandHandler mapManager.getNodeDescription(locationId)
            }

            val targetInput = args.joinToString(" ")

            // 支持按节点 key 或显示名匹配
            val currentNodeId = mapManager.getPlayerLocation(ctx.playerId)
                ?: return@CommandHandler "位置未初始化。"
            val currentNode = mapManager.getNode(currentNodeId)
                ?: return@CommandHandler "当前节点异常。"

            // 查找目标：先精确匹配 key，再模糊匹配 displayName
            val targetNodeId = currentNode.connections.find { conn ->
                conn.target == targetInput
            }?.target ?: run {
                // 按显示名查找
                val matched = currentNode.connections.find { conn ->
                    val node = mapManager.getNode(conn.target)
                    node?.displayName == targetInput || node?.displayName?.contains(targetInput) == true
                }
                matched?.target
            }

            if (targetNodeId == null) {
                val available = currentNode.connections.joinToString("、") { conn ->
                    mapManager.getNode(conn.target)?.displayName ?: conn.target
                }
                return@CommandHandler "无法前往「$targetInput」。\n可前往：$available"
            }

            val moveResult = mapManager.move(ctx.playerId, targetNodeId, ctx)
            if (moveResult.success) {
                moveResult.message ?: "已移动。"
            } else {
                moveResult.message ?: "移动失败。"
            }
        })

        // ==================== /背包 ====================
        registry.registerBuiltin("inventory", CommandHandler { _, context ->
            val ctx = context as PlayerContext
            val items = itemRepository.findByPlayerId(ctx.playerId)
                .filter { it.slotType == org.textrpg.application.domain.model.SlotType.INVENTORY }

            if (items.isEmpty()) {
                return@CommandHandler "━━━ 背包 ━━━\n（空空如也）"
            }

            // 预加载所有模板
            val allTemplates = itemRepository.findAllTemplates().associateBy { it.id }

            buildString {
                appendLine("━━━ 背包 ━━━")
                for (item in items) {
                    val template = allTemplates[item.templateId]
                    val name = template?.name ?: "未知物品"
                    val typeSuffix = when (template?.type) {
                        ItemType.EQUIPMENT -> " [装备]"
                        ItemType.CONSUMABLE -> " [消耗品]"
                        ItemType.MATERIAL -> " [材料]"
                        ItemType.QUEST -> " [任务]"
                        null -> ""
                    }
                    if (item.quantity > 1) {
                        appendLine("  $name x${item.quantity}$typeSuffix")
                    } else {
                        appendLine("  $name$typeSuffix")
                    }
                }
            }.trimEnd()
        })

        // ==================== /装备 ====================
        registry.registerBuiltin("equip", CommandHandler { args, context ->
            val ctx = context as PlayerContext
            val attrs = ctx.attributeContainer
                ?: return@CommandHandler "属性数据异常。"

            if (args.isEmpty()) {
                // 无参数：显示当前装备栏
                return@CommandHandler showEquipment(ctx.playerId, itemRepository, equipmentService)
            }

            val itemName = args.joinToString(" ")

            // 在背包中查找匹配物品
            val invItems = itemRepository.findByPlayerId(ctx.playerId)
                .filter { it.slotType == org.textrpg.application.domain.model.SlotType.INVENTORY }

            val allTemplates = itemRepository.findAllTemplates().associateBy { it.id }

            val matched = invItems.find { item ->
                val template = allTemplates[item.templateId]
                template?.name == itemName || template?.name?.contains(itemName) == true
            }

            if (matched == null) {
                return@CommandHandler "背包中没有找到「$itemName」。"
            }

            val template = allTemplates[matched.templateId]
                ?: return@CommandHandler "物品数据异常。"

            if (template.type != ItemType.EQUIPMENT) {
                return@CommandHandler "「${template.name}」不是装备，无法穿戴。"
            }
            if (matched.instanceId == null) {
                return@CommandHandler "物品实例异常。"
            }

            // 自动选择装备槽位
            val slot = autoSelectSlot(template.subType)
                ?: return@CommandHandler "无法确定「${template.name}」的装备槽位。"

            val result = equipmentService.equip(ctx.playerId, matched.id, slot, attrs)
            if (result.success) {
                val stats = ItemStatsParser.parseBaseStats(template.baseStats)
                val statText = if (stats.isNotEmpty()) {
                    "\n属性加成：" + stats.entries.joinToString("、") { "${it.key} +${it.value.toInt()}" }
                } else ""
                "已装备「${template.name}」→ ${slotDisplayName(slot)}$statText"
            } else {
                result.message ?: "装备失败。"
            }
        })

        // ==================== /卸下 ====================
        registry.registerBuiltin("unequip", CommandHandler { args, context ->
            val ctx = context as PlayerContext
            val attrs = ctx.attributeContainer
                ?: return@CommandHandler "属性数据异常。"

            if (args.isEmpty()) {
                return@CommandHandler "请指定要卸下的槽位。\n用法：/卸下 <武器/防具/...>"
            }

            val slotName = args.joinToString(" ")
            val slot = parseSlotName(slotName)
                ?: return@CommandHandler "未知的槽位「$slotName」。\n可选：武器、防具、头盔、鞋子、手套、戒指、项链、副手"

            val result = equipmentService.unequip(ctx.playerId, slot, attrs)
            if (result.success) {
                "已卸下 ${slotDisplayName(slot)} 的装备。"
            } else {
                result.message ?: "卸下失败。"
            }
        })
    }

    // ==================== 辅助方法 ====================

    /**
     * 显示装备栏
     */
    private fun showEquipment(
        playerId: Long,
        itemRepository: ItemRepository,
        equipmentService: EquipmentService
    ): String {
        val equipment = equipmentService.getEquipment(playerId)
            ?: return "━━━ 装备栏 ━━━\n（全部空置）"

        val allTemplates = itemRepository.findAllTemplates().associateBy { it.id }

        fun slotInfo(slot: EquipmentSlot): String {
            val instanceId = equipment.getSlot(slot) ?: return "（空）"
            val instance = itemRepository.findInstanceById(instanceId) ?: return "（异常）"
            val template = allTemplates[instance.templateId] ?: return "（未知）"
            return template.name
        }

        return buildString {
            appendLine("━━━ 装备栏 ━━━")
            appendLine("武器：${slotInfo(EquipmentSlot.WEAPON)}")
            appendLine("防具：${slotInfo(EquipmentSlot.CHEST)}")
            appendLine("头盔：${slotInfo(EquipmentSlot.HEAD)}")
            appendLine("鞋子：${slotInfo(EquipmentSlot.BOOTS)}")
            appendLine("手套：${slotInfo(EquipmentSlot.GLOVES)}")
            appendLine("副手：${slotInfo(EquipmentSlot.OFFHAND)}")
            appendLine("戒指：${slotInfo(EquipmentSlot.RING)}")
            appendLine("项链：${slotInfo(EquipmentSlot.AMULET)}")
        }.trimEnd()
    }

    /**
     * 根据物品子类型自动选择装备槽位
     */
    private fun autoSelectSlot(subType: org.textrpg.application.domain.model.ItemSubType): EquipmentSlot? {
        return when (subType) {
            org.textrpg.application.domain.model.ItemSubType.WEAPON -> EquipmentSlot.WEAPON
            org.textrpg.application.domain.model.ItemSubType.ARMOR -> EquipmentSlot.CHEST
            org.textrpg.application.domain.model.ItemSubType.JEWELRY -> EquipmentSlot.RING
        }
    }

    /**
     * 解析槽位名称
     */
    private fun parseSlotName(name: String): EquipmentSlot? {
        return when (name) {
            "武器" -> EquipmentSlot.WEAPON
            "防具", "铠甲", "胸甲" -> EquipmentSlot.CHEST
            "头盔", "头部" -> EquipmentSlot.HEAD
            "鞋子", "靴子" -> EquipmentSlot.BOOTS
            "手套" -> EquipmentSlot.GLOVES
            "副手", "盾牌" -> EquipmentSlot.OFFHAND
            "戒指" -> EquipmentSlot.RING
            "项链", "护符" -> EquipmentSlot.AMULET
            else -> null
        }
    }

    /**
     * 槽位显示名称
     */
    private fun slotDisplayName(slot: EquipmentSlot): String {
        return when (slot) {
            EquipmentSlot.WEAPON -> "武器"
            EquipmentSlot.CHEST -> "防具"
            EquipmentSlot.HEAD -> "头盔"
            EquipmentSlot.BOOTS -> "鞋子"
            EquipmentSlot.GLOVES -> "手套"
            EquipmentSlot.OFFHAND -> "副手"
            EquipmentSlot.RING -> "戒指"
            EquipmentSlot.AMULET -> "项链"
        }
    }
}

fun main() {
    runBlocking { Application.loop() }
}

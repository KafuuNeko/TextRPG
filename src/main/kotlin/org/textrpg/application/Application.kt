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
import org.textrpg.application.game.combat.CombatSessionFactory
import org.textrpg.application.game.combat.EnemyAI
import org.textrpg.application.game.command.*
import org.textrpg.application.game.effect.EffectEngine
import org.textrpg.application.game.equipment.EquipmentService
import org.textrpg.application.game.inventory.InventoryService
import org.textrpg.application.game.inventory.ItemStatsParser
import org.textrpg.application.game.map.MapManager
import org.textrpg.application.game.npc.NpcManager
import org.textrpg.application.game.player.ItemSeeder
import org.textrpg.application.adapter.llm.model.LLMMessage
import org.textrpg.application.domain.model.MapNodeDefinition
import org.textrpg.application.domain.model.NodeConnection
import org.textrpg.application.game.player.PlayerContext
import org.textrpg.application.game.player.PlayerManager
import org.textrpg.application.game.quest.QuestEvent
import org.textrpg.application.game.quest.QuestManager
import org.textrpg.application.game.quest.QuestStatus
import org.textrpg.application.game.skill.CooldownManager
import org.textrpg.application.game.skill.SkillEngine
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
        val skillConfig = SkillConfigLoader.load()
        val buffConfig = BuffConfigLoader.load()
        val combatConfigResult = CombatConfigLoader.load()
        val npcConfig = NpcConfigLoader.load()
        val questConfig = QuestConfigLoader.load()

        println("已加载 ${attributeDefinitions.size} 个属性，${commandConfig.commands.size} 个指令，${mapConfig.nodes.size} 个地图节点")
        println("已加载 ${skillConfig.skills.size} 个技能，${buffConfig.buffs.size} 个Buff，${combatConfigResult.enemies.size} 个敌人")
        println("已加载 ${npcConfig.npcs.size} 个NPC，${questConfig.quests.size} 个任务")

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

        // 战斗子系统
        val scriptRunner: KotlinScriptRunner = get()
        val effectEngine = EffectEngine(scriptRunner)
        val cooldownManager = CooldownManager() // 全局 CD（敌人用）
        val skillEngine = SkillEngine(effectEngine, cooldownManager)
        skillEngine.loadSkills(skillConfig)
        val enemyAI = EnemyAI(scriptRunner)
        val combatSessionFactory = CombatSessionFactory(
            combatConfig = combatConfigResult.combatConfig,
            enemyDefinitions = combatConfigResult.enemies,
            skillEngine = skillEngine,
            effectEngine = effectEngine,
            sessionManager = sessionManager,
            enemyAI = enemyAI,
            buffDefinitions = buffConfig.buffs,
            attributeDefinitions = attributeDefinitions,
            coroutineScope = gameScope
        )

        // NPC / 任务子系统
        val npcManager = NpcManager(npcConfig)
        val questManager = QuestManager(questConfig.quests)

        val playerManager = PlayerManager(
            playerRepository = playerRepository,
            attributeDefinitions = attributeDefinitions,
            buffDefinitions = buffConfig.buffs,
            sessionManager = sessionManager,
            mapManager = mapManager,
            inventoryService = inventoryService
        )

        // ========== 注册指令处理器 ==========
        val llmClient: LLMClient = get()
        val aiConfig = AIConfigLoader.load()

        registerHandlers(
            registry = handlerRegistry,
            playerManager = playerManager,
            mapManager = mapManager,
            itemRepository = itemRepository,
            equipmentService = equipmentService,
            combatSessionFactory = combatSessionFactory,
            combatConfigResult = combatConfigResult,
            skillConfig = skillConfig,
            npcManager = npcManager,
            questManager = questManager,
            questConfig = questConfig,
            llmClient = llmClient,
            aiConfig = aiConfig
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
        equipmentService: EquipmentService,
        combatSessionFactory: CombatSessionFactory,
        combatConfigResult: CombatConfigResult,
        skillConfig: SkillConfig,
        npcManager: NpcManager,
        questManager: QuestManager,
        questConfig: QuestConfig,
        llmClient: LLMClient,
        aiConfig: AIConfig
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
                // 触发任务事件：到达节点
                questManager.onEvent(QuestEvent.NodeReached(ctx.playerId, targetNodeId))
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

        // ==================== /战斗 ====================
        registry.registerBuiltin("battle", CommandHandler { args, context ->
            val ctx = context as PlayerContext
            val attrs = ctx.attributeContainer
                ?: return@CommandHandler "属性数据异常。"

            // 检查是否已在战斗中
            if (ctx.currentSessionType == "combat") {
                return@CommandHandler "你已经在战斗中了！"
            }

            // 获取当前节点的敌人列表
            val locationId = ctx.mapManager.getPlayerLocation(ctx.playerId)
                ?: return@CommandHandler "位置未初始化。"
            val node = ctx.mapManager.getNode(locationId)
                ?: return@CommandHandler "当前节点异常。"

            val availableEnemies = node.entities.enemies
            if (availableEnemies.isEmpty()) {
                return@CommandHandler "这里没有可以战斗的敌人。\n提示：前往带有敌人的区域再试试。"
            }

            // 匹配敌人
            val enemyId: String
            if (args.isEmpty()) {
                if (availableEnemies.size == 1) {
                    enemyId = availableEnemies.first()
                } else {
                    val enemyNames = availableEnemies.mapNotNull { id ->
                        combatConfigResult.enemies[id]?.displayName?.let { "$it ($id)" }
                    }
                    return@CommandHandler "这里有多个敌人，请指定目标：\n${enemyNames.joinToString("\n") { "  $it" }}\n用法：/战斗 <敌人名>"
                }
            } else {
                val input = args.joinToString(" ")
                enemyId = availableEnemies.find { id ->
                    id == input || combatConfigResult.enemies[id]?.displayName == input
                        || combatConfigResult.enemies[id]?.displayName?.contains(input) == true
                } ?: return@CommandHandler "这里没有「$input」。"
            }

            // 检查敌人定义是否存在
            if (combatConfigResult.enemies[enemyId] == null) {
                return@CommandHandler "敌人数据异常：$enemyId"
            }

            // 玩家可用技能（全部技能）
            val playerSkillIds = skillConfig.skills.keys.toList()

            // 创建并启动战斗会话
            val session = combatSessionFactory.createAndStart(
                playerId = ctx.playerId,
                enemyId = enemyId,
                playerEntity = object : org.textrpg.application.game.buff.BuffAwareEntityAccessor(
                    attrs, ctx.buffManager ?: org.textrpg.application.game.buff.BuffManager(emptyMap(), attrs)
                ) {
                    override val entityId: String = ctx.playerId.toString()
                    override fun hasItem(itemId: String, quantity: Int) = false
                    override fun giveItem(itemId: String, quantity: Int) {}
                    override fun removeItem(itemId: String, quantity: Int) {}
                    override suspend fun sendMessage(message: String) { ctx.reply(message) }
                },
                playerSkillIds = playerSkillIds,
                playerCooldownManager = ctx.cooldownManager,
                messageSink = { msg -> ctx.reply(msg) }
            )

            if (session == null) {
                "战斗创建失败，敌人不存在。"
            } else {
                // 战斗会话已注册，后续消息由 CombatSession 接管
                null
            }
        })

        // ==================== /对话 ====================
        registry.registerBuiltin("dialogue", CommandHandler { args, context ->
            val ctx = context as PlayerContext
            val locationId = ctx.mapManager.getPlayerLocation(ctx.playerId)
                ?: return@CommandHandler "位置未初始化。"

            val npcsHere = npcManager.getNpcsAtNode(locationId)
            if (npcsHere.isEmpty()) {
                return@CommandHandler "这里没有可以对话的人。"
            }

            if (args.isEmpty()) {
                if (npcsHere.size == 1) {
                    val npc = npcsHere.first()
                    questManager.onEvent(QuestEvent.NpcTalked(ctx.playerId, npc.key))
                    // AI 对话
                    return@CommandHandler generateNpcDialogue(llmClient, aiConfig, npc.displayName, npc.prompt, "你好")
                }
                val names = npcsHere.joinToString("、") { it.displayName }
                return@CommandHandler "这里有：$names\n用法：/对话 <NPC名> [想说的话]"
            }

            val input = args.joinToString(" ")
            // 分离 NPC 名称和对话内容：第一个匹配到的 NPC 名后面都是对话内容
            var npcMatch = npcsHere.find { input.startsWith(it.displayName) }
            val playerMessage: String
            if (npcMatch != null) {
                playerMessage = input.removePrefix(npcMatch.displayName).trim().ifEmpty { "你好" }
            } else {
                // 尝试模糊匹配
                npcMatch = npcsHere.find { it.displayName.contains(input.split(" ").first()) || it.key == input.split(" ").first() }
                if (npcMatch != null) {
                    playerMessage = input.split(" ").drop(1).joinToString(" ").ifEmpty { "你好" }
                } else {
                    return@CommandHandler "这里没有「${input.split(" ").first()}」。"
                }
            }

            questManager.onEvent(QuestEvent.NpcTalked(ctx.playerId, npcMatch.key))
            generateNpcDialogue(llmClient, aiConfig, npcMatch.displayName, npcMatch.prompt, playerMessage)
        })

        // ==================== /任务 ====================
        registry.registerBuiltin("quest", CommandHandler { args, context ->
            val ctx = context as PlayerContext
            val action = args.firstOrNull() ?: ""

            when (action) {
                "接取", "accept" -> {
                    // 接取任务：查找当前节点 NPC 提供的任务
                    val locationId = ctx.mapManager.getPlayerLocation(ctx.playerId) ?: return@CommandHandler "位置未初始化。"
                    val npcsHere = npcManager.getNpcsAtNode(locationId)
                    val availableQuests = npcsHere.flatMap { npc ->
                        npc.functions
                            .filter { it.type == "quest_giver" }
                            .flatMap { func -> func.params["quest_ids"]?.split(",")?.map { it.trim() } ?: emptyList() }
                    }
                    if (availableQuests.isEmpty()) {
                        return@CommandHandler "这里没有可接取的任务。"
                    }
                    // 尝试接取所有可用任务中的第一个未接取的
                    for (qid in availableQuests) {
                        val status = questManager.getQuestStatus(ctx.playerId, qid)
                        if (status == null || status == QuestStatus.TURNED_IN) {
                            val result = questManager.acceptQuest(ctx.playerId, qid, ctx)
                            return@CommandHandler result.message ?: "操作完成。"
                        }
                    }
                    "当前没有可接取的新任务。"
                }
                "交付", "turnin" -> {
                    // 交付已完成的任务
                    val activeQuests = questManager.getActiveQuests(ctx.playerId)
                    val completedQuest = activeQuests.find { it.status == QuestStatus.COMPLETED }
                    if (completedQuest == null) {
                        return@CommandHandler "没有可交付的任务。"
                    }
                    // 创建简易 EntityAccessor 用于发放奖励
                    val attrs = ctx.attributeContainer ?: return@CommandHandler "属性数据异常。"
                    val accessor = object : org.textrpg.application.game.buff.BuffAwareEntityAccessor(
                        attrs, ctx.buffManager ?: org.textrpg.application.game.buff.BuffManager(emptyMap(), attrs)
                    ) {
                        override val entityId: String = ctx.playerId.toString()
                        override fun hasItem(itemId: String, quantity: Int) = ctx.inventoryService.hasItem(ctx.playerId, itemId.toIntOrNull() ?: 0, quantity)
                        override fun giveItem(itemId: String, quantity: Int) {
                            val tid = itemId.toIntOrNull() ?: return
                            ctx.inventoryService.addItem(ctx.playerId, tid, quantity)
                        }
                        override fun removeItem(itemId: String, quantity: Int) {
                            val tid = itemId.toIntOrNull() ?: return
                            ctx.inventoryService.removeItem(ctx.playerId, tid, quantity)
                        }
                        override suspend fun sendMessage(message: String) { ctx.reply(message) }
                    }
                    val result = questManager.turnInQuest(ctx.playerId, completedQuest.questId, accessor)
                    result.message ?: "操作完成。"
                }
                else -> {
                    // 默认：显示当前任务列表
                    val activeQuests = questManager.getActiveQuests(ctx.playerId)
                    if (activeQuests.isEmpty()) {
                        return@CommandHandler "━━━ 任务 ━━━\n（没有进行中的任务）\n提示：与 NPC 对话后输入 /任务 接取"
                    }
                    buildString {
                        appendLine("━━━ 任务 ━━━")
                        for (progress in activeQuests) {
                            val def = questConfig.quests[progress.questId]
                            val name = def?.displayName ?: progress.questId
                            val statusText = when (progress.status) {
                                QuestStatus.ACTIVE -> "进行中"
                                QuestStatus.COMPLETED -> "已完成（可交付）"
                                else -> progress.status.name
                            }
                            appendLine("  $name [$statusText]")
                            if (def != null) {
                                for ((i, obj) in def.objectives.withIndex()) {
                                    val objProgress = progress.objectives.getOrNull(i)
                                    val cur = objProgress?.current ?: 0
                                    val req = objProgress?.required ?: obj.quantity
                                    appendLine("    - ${obj.type.value}: $cur / $req")
                                }
                            }
                        }
                    }.trimEnd()
                }
            }
        })

        // ==================== /商店 ====================
        registry.registerBuiltin("shop", CommandHandler { args, context ->
            val ctx = context as PlayerContext
            val locationId = ctx.mapManager.getPlayerLocation(ctx.playerId) ?: return@CommandHandler "位置未初始化。"
            val npcsHere = npcManager.getNpcsAtNode(locationId)

            // 找到有商店功能的 NPC
            val shopNpc = npcsHere.find { npc ->
                npc.functions.any { it.type == "shop" }
            }
            if (shopNpc == null) {
                return@CommandHandler "这里没有商店。"
            }
            val shopFunc = shopNpc.functions.first { it.type == "shop" }
            val shopItemIds = shopFunc.params["items"]?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
            val allTemplates = itemRepository.findAllTemplates().associateBy { it.id }
            val shopItems = shopItemIds.mapNotNull { allTemplates[it] }

            val action = args.firstOrNull() ?: ""
            val itemName = args.drop(1).joinToString(" ")

            when (action) {
                "买", "buy" -> {
                    if (itemName.isBlank()) {
                        return@CommandHandler "请指定物品名。\n用法：/商店 买 <物品名>"
                    }
                    val template = shopItems.find { it.name == itemName || it.name.contains(itemName) }
                        ?: return@CommandHandler "商店没有「$itemName」。"

                    val gold = ctx.getAttributeValue("gold")?.toInt() ?: 0
                    if (gold < template.price) {
                        return@CommandHandler "金币不足（需要 ${template.price}，当前 $gold）。"
                    }
                    ctx.modifyAttribute("gold", -template.price.toDouble())
                    ctx.inventoryService.addItem(ctx.playerId, template.id)
                    "购买了「${template.name}」，花费 ${template.price} 金币。"
                }
                "卖", "sell" -> {
                    if (itemName.isBlank()) {
                        return@CommandHandler "请指定物品名。\n用法：/商店 卖 <物品名>"
                    }
                    val invItems = itemRepository.findByPlayerId(ctx.playerId)
                        .filter { it.slotType == org.textrpg.application.domain.model.SlotType.INVENTORY }
                    val matched = invItems.find { item ->
                        val tmpl = allTemplates[item.templateId]
                        tmpl?.name == itemName || tmpl?.name?.contains(itemName) == true
                    }
                    if (matched == null) {
                        return@CommandHandler "背包中没有「$itemName」。"
                    }
                    val template = allTemplates[matched.templateId] ?: return@CommandHandler "物品数据异常。"
                    val sellPrice = template.price / 2
                    itemRepository.delete(matched.id)
                    ctx.modifyAttribute("gold", sellPrice.toDouble())
                    "卖出了「${template.name}」，获得 $sellPrice 金币。"
                }
                else -> {
                    // 默认：显示商店
                    buildString {
                        appendLine("━━━ ${shopNpc.displayName}的商店 ━━━")
                        val gold = ctx.getAttributeValue("gold")?.toInt() ?: 0
                        appendLine("你的金币：$gold")
                        appendLine()
                        for (item in shopItems) {
                            appendLine("  ${item.name}  ${item.price} 金币")
                            if (item.description.isNotBlank()) appendLine("    ${item.description}")
                        }
                        appendLine()
                        appendLine("用法：/商店 买 <物品名>  |  /商店 卖 <物品名>")
                    }.trimEnd()
                }
            }
        })
        // ==================== /探索 ====================
        registry.registerBuiltin("explore", CommandHandler { _, context ->
            val ctx = context as PlayerContext
            val locationId = ctx.mapManager.getPlayerLocation(ctx.playerId)
                ?: return@CommandHandler "位置未初始化。"
            val currentNode = ctx.mapManager.getNode(locationId)
                ?: return@CommandHandler "当前节点异常。"

            if (!currentNode.isBoundary) {
                return@CommandHandler "这里不是边界区域，无法探索未知领域。"
            }

            // 通知玩家正在生成
            ctx.reply("正在探索未知区域……")

            // 调用 LLM 生成新区域
            val scenePrompt = aiConfig.scenes["world_generation"]?.promptTemplate ?: ""
            val contextInfo = "当前位置：${currentNode.displayName}（${currentNode.description}）"

            val messages = listOf(
                LLMMessage.System(scenePrompt),
                LLMMessage.User(contextInfo)
            )

            val result = llmClient.generate(messages)
            if (result.isFailure) {
                return@CommandHandler "探索失败……迷雾太浓了，什么都看不清。"
            }

            val responseText = result.getOrDefault("")
            // 尝试解析 JSON 格式的节点数据
            val newNode = parseGeneratedNode(responseText, locationId)
            if (newNode != null) {
                mapManager.addNode(newNode)
                // 添加从当前节点到新节点的连接
                val updatedCurrentNode = currentNode.copy(
                    connections = currentNode.connections + NodeConnection(
                        target = newNode.key,
                        display = "→ ${newNode.displayName}"
                    )
                )
                mapManager.addNode(updatedCurrentNode)
                // 自动移动到新区域
                mapManager.setPlayerLocation(ctx.playerId, newNode.key)

                buildString {
                    appendLine("━━━ 发现新区域 ━━━")
                    appendLine("【${newNode.displayName}】")
                    appendLine(newNode.description)
                    appendLine()
                    appendLine("你进入了这片新区域。")
                    appendLine("输入 /移动 查看可前往的方向。")
                }.trimEnd()
            } else {
                // JSON 解析失败，直接展示 AI 文本
                "━━━ 探索结果 ━━━\n$responseText\n\n（区域数据未能自动创建，但你看到了这片景象。）"
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
    /**
     * 调用 LLM 生成 NPC 对话回复
     */
    private suspend fun generateNpcDialogue(
        llmClient: LLMClient,
        aiConfig: AIConfig,
        npcName: String,
        npcPrompt: String,
        playerMessage: String
    ): String {
        val scenePrompt = aiConfig.scenes["npc_dialogue"]?.promptTemplate ?: ""
        val messages = listOf(
            LLMMessage.System(scenePrompt),
            LLMMessage.System("你扮演的角色：$npcPrompt"),
            LLMMessage.User(playerMessage)
        )
        val result = llmClient.generate(messages)
        return if (result.isSuccess) {
            val reply = result.getOrDefault("……")
            "【$npcName】$reply"
        } else {
            "【$npcName】……（NPC 似乎在思考什么，没有回应。）"
        }
    }

    /**
     * 解析 AI 生成的节点 JSON
     */
    private fun parseGeneratedNode(responseText: String, parentNodeId: String): MapNodeDefinition? {
        return try {
            // 提取 JSON（可能包裹在 markdown code block 中）
            val jsonStr = responseText
                .replace("```json", "").replace("```", "")
                .trim()
            val gson = com.google.gson.Gson()
            val json = gson.fromJson(jsonStr, com.google.gson.JsonObject::class.java)

            val name = json.get("name")?.asString ?: return null
            val description = json.get("description")?.asString ?: ""
            val tags = json.getAsJsonArray("tags")?.map { it.asString }?.toSet() ?: emptySet()

            // 生成唯一 key
            val key = "gen_${System.currentTimeMillis()}"

            MapNodeDefinition(
                key = key,
                displayName = name,
                description = description,
                tags = tags,
                connections = listOf(NodeConnection(target = parentNodeId, display = "→ 返回")),
                isBoundary = true // 生成的节点也可以继续探索
            )
        } catch (e: Exception) {
            println("Warning: Failed to parse generated node: ${e.message}")
            null
        }
    }

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

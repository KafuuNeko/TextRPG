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
import org.textrpg.application.domain.model.NodeEntities
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

        // ========== 消息处理核心（OneBot / 控制台共用） ==========
        val processMessage: suspend (String, String, suspend (String) -> Unit) -> Unit =
            { rawText, bindAccount, replier ->
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
                    println("消息处理异常 [$bindAccount]: ${e.message}")
                    e.printStackTrace()
                }
            }

        val appConfig: AppConfig = get()

        if (appConfig.bot.enabled) {
            // ========== OneBot 模式 ==========
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
                gameScope.launch { processMessage(rawText, bindAccount, replier) }
            }

            println("TextRPG 启动完成，等待 OneBot 连接...")
            try {
                adapter.connect()
            } catch (e: Exception) {
                println("OneBot 连接失败: ${e.localizedMessage}")
                println("提示：将 app.yaml 中 bot.enabled 设为 false 可进入控制台模式")
            }
        } else {
            // ========== 控制台模式 ==========
            println("━━━━━━━━━━━━━━━━━━━━━━━━")
            println("  TextRPG 控制台模式")
            println("━━━━━━━━━━━━━━━━━━━━━━━━")
            println("直接输入指令即可游玩（如 /注册 勇者）")
            println("输入 quit 退出")
            println()

            val consoleBindAccount = "console_player"
            val consolePrinter: suspend (String) -> Unit = { msg -> println(msg) }
            val reader = java.io.BufferedReader(java.io.InputStreamReader(System.`in`, Charsets.UTF_8))

            while (true) {
                print("> ")
                System.out.flush()
                val line = try { reader.readLine() } catch (_: Exception) { null }
                if (line == null) break
                val trimmed = line.trim()
                if (trimmed.equals("quit", ignoreCase = true) || trimmed.equals("exit", ignoreCase = true)) {
                    playerManager.saveAll()
                    println("已保存数据，再见！")
                    break
                }
                if (trimmed.isBlank()) continue
                processMessage(trimmed, consoleBindAccount, consolePrinter)
            }
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
        // ==================== 辅助闭包 ====================

        /**
         * 构建当前节点的完整信息视图：方向、NPC（含任务/商店标签）、敌人、边界提示。
         * 每个列表都带编号，可被对应指令用作索引。
         */
        val buildRichNodeInfo: (PlayerContext) -> String = buildRichNodeInfo@{ ctx ->
            val locationId = mapManager.getPlayerLocation(ctx.playerId)
                ?: return@buildRichNodeInfo "位置未初始化。"
            val node = mapManager.getNode(locationId)
                ?: return@buildRichNodeInfo "节点数据异常。"

            val availableConns = mapManager.getAvailableConnections(ctx.playerId, ctx)
            val npcsHere = npcManager.getNpcsAtNode(locationId)
            val enemiesHere = node.entities.enemies
            val hasShop = npcsHere.any { npc -> npc.functions.any { it.type == "shop" } }

            buildString {
                appendLine("━━━【${node.displayName}】━━━")
                if (node.description.isNotBlank()) {
                    appendLine(node.description)
                }

                // 可前往方向
                if (availableConns.isNotEmpty()) {
                    appendLine()
                    appendLine("〖可前往〗")
                    availableConns.forEachIndexed { i, conn ->
                        val targetName = mapManager.getNode(conn.target)?.displayName ?: conn.target
                        appendLine("  ${i + 1}. → $targetName")
                    }
                }

                // 此处 NPC（含商店/可接任务/可交付标签）
                if (npcsHere.isNotEmpty()) {
                    appendLine()
                    appendLine("〖此处的人〗")
                    npcsHere.forEachIndexed { i, npc ->
                        val tags = mutableListOf<String>()
                        if (npc.functions.any { it.type == "shop" }) tags.add("商店")

                        val giverQuestIds = npc.functions
                            .filter { it.type == "quest_giver" }
                            .flatMap { func ->
                                func.params["quest_ids"]?.split(",")?.map { it.trim() } ?: emptyList()
                            }

                        val acceptable = giverQuestIds.filter { qid ->
                            val status = questManager.getQuestStatus(ctx.playerId, qid)
                            status == null || status == QuestStatus.TURNED_IN
                        }.mapNotNull { qid -> questConfig.quests[qid]?.displayName }
                        if (acceptable.isNotEmpty()) {
                            tags.add("可接任务：${acceptable.joinToString("/")}")
                        }

                        val turnIn = giverQuestIds.filter { qid ->
                            questManager.isQuestCompleted(ctx.playerId, qid)
                        }.mapNotNull { qid -> questConfig.quests[qid]?.displayName }
                        if (turnIn.isNotEmpty()) {
                            tags.add("可交付：${turnIn.joinToString("/")}")
                        }

                        val tagText = if (tags.isNotEmpty()) "  · ${tags.joinToString(" · ")}" else ""
                        appendLine("  ${i + 1}. ${npc.displayName}$tagText")
                    }
                }

                // 此处敌人
                if (enemiesHere.isNotEmpty()) {
                    appendLine()
                    appendLine("〖此处的敌人〗")
                    enemiesHere.forEachIndexed { i, enemyId ->
                        val name = combatConfigResult.enemies[enemyId]?.displayName ?: enemyId
                        appendLine("  ${i + 1}. $name")
                    }
                }

                // 边界提示
                if (node.isBoundary) {
                    appendLine()
                    appendLine("（此处是边界区域，可使用 /探索 开拓新天地）")
                }

                // 操作提示
                val hints = mutableListOf<String>()
                if (availableConns.isNotEmpty()) hints.add("/移动 <编号>")
                if (npcsHere.isNotEmpty()) hints.add("/对话 <编号>")
                if (enemiesHere.isNotEmpty()) hints.add("/战斗 <编号>")
                if (hasShop) hints.add("/商店")
                if (hints.isNotEmpty()) {
                    appendLine()
                    appendLine("操作提示：${hints.joinToString(" · ")}")
                }
            }.trimEnd()
        }

        /**
         * 从列表中按编号或名字提取项。编号为 1-based。
         * 返回 null 表示未匹配。
         */
        fun <T> pickFromList(input: String, list: List<T>, nameMatcher: (T, String) -> Boolean): T? {
            if (input.isBlank()) return null
            val asIndex = input.toIntOrNull()
            if (asIndex != null) return list.getOrNull(asIndex - 1)
            return list.find { nameMatcher(it, input) }
        }

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
                appendLine("输入 /移动 查看周围环境并开始探索")
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

            // 无参数时显示当前节点全貌（rich info）
            if (args.isEmpty()) {
                return@CommandHandler buildRichNodeInfo(ctx)
            }

            val targetInput = args.joinToString(" ")

            // 可用连接列表（已过滤 Requires）—— 这也是编号的依据
            val availableConns = mapManager.getAvailableConnections(ctx.playerId, ctx)

            // 按编号 / 名字 / key 匹配
            val targetConn = pickFromList(targetInput, availableConns) { conn, input ->
                val node = mapManager.getNode(conn.target)
                conn.target == input
                    || node?.displayName == input
                    || node?.displayName?.contains(input) == true
            }

            if (targetConn == null) {
                val listText = availableConns.mapIndexed { i, conn ->
                    val name = mapManager.getNode(conn.target)?.displayName ?: conn.target
                    "  ${i + 1}. $name"
                }.joinToString("\n")
                return@CommandHandler buildString {
                    appendLine("无法前往「$targetInput」。")
                    if (availableConns.isNotEmpty()) {
                        appendLine("可前往：")
                        append(listText)
                    } else {
                        append("（当前没有可前往的方向）")
                    }
                }
            }

            val moveResult = mapManager.move(ctx.playerId, targetConn.target, ctx)
            if (moveResult.success) {
                questManager.onEvent(QuestEvent.NodeReached(ctx.playerId, targetConn.target))
                // 移动成功：展示新节点的完整信息
                buildRichNodeInfo(ctx)
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

            val allTemplates = itemRepository.findAllTemplates().associateBy { it.id }

            // 背包中所有装备类型的物品（编号列表来源）
            val equippableItems = itemRepository.findByPlayerId(ctx.playerId)
                .filter { it.slotType == org.textrpg.application.domain.model.SlotType.INVENTORY }
                .filter { allTemplates[it.templateId]?.type == ItemType.EQUIPMENT }

            if (args.isEmpty()) {
                // 无参数：显示装备栏 + 可装备列表
                val equipmentPanel = showEquipment(ctx.playerId, itemRepository, equipmentService)
                val availablePanel = if (equippableItems.isEmpty()) {
                    "\n\n〖可装备的物品〗\n  (背包中没有可装备物品)"
                } else {
                    buildString {
                        appendLine()
                        appendLine()
                        appendLine("〖可装备的物品〗")
                        equippableItems.forEachIndexed { i, item ->
                            val tmpl = allTemplates[item.templateId]
                            val name = tmpl?.name ?: "未知"
                            val slotHint = tmpl?.subType?.let { autoSelectSlot(it) }?.let { slotDisplayName(it) } ?: "—"
                            appendLine("  ${i + 1}. $name  [$slotHint]")
                        }
                        append("\n用法：/装备 <编号 或 名字>")
                    }.trimEnd()
                }
                return@CommandHandler equipmentPanel + availablePanel
            }

            val input = args.joinToString(" ")

            val matched = pickFromList(input, equippableItems) { item, inp ->
                val tmpl = allTemplates[item.templateId]
                tmpl?.name == inp || tmpl?.name?.contains(inp) == true
            } ?: return@CommandHandler "背包中没有可装备的「$input」。"

            val template = allTemplates[matched.templateId]
                ?: return@CommandHandler "物品数据异常。"
            if (matched.instanceId == null) {
                return@CommandHandler "物品实例异常。"
            }

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

            // 收集当前已装备的槽位列表（编号来源）
            val equipment = equipmentService.getEquipment(ctx.playerId)
            val occupiedSlots = EquipmentSlot.entries.filter { equipment?.getSlot(it) != null }

            if (args.isEmpty()) {
                if (occupiedSlots.isEmpty()) {
                    return@CommandHandler "当前没有穿戴任何装备。"
                }
                val allTemplates = itemRepository.findAllTemplates().associateBy { it.id }
                val panel = buildString {
                    appendLine("〖可卸下的装备〗")
                    occupiedSlots.forEachIndexed { i, slot ->
                        val instanceId = equipment?.getSlot(slot)
                        val name = instanceId?.let { id ->
                            itemRepository.findInstanceById(id)?.let { inst ->
                                allTemplates[inst.templateId]?.name
                            }
                        } ?: "—"
                        appendLine("  ${i + 1}. ${slotDisplayName(slot)}：$name")
                    }
                    append("\n用法：/卸下 <编号 或 槽位名>")
                }.trimEnd()
                return@CommandHandler panel
            }

            val input = args.joinToString(" ")

            // 先尝试编号
            val slot = pickFromList(input, occupiedSlots) { s, inp ->
                // 名字匹配走 parseSlotName
                parseSlotName(inp) == s
            } ?: parseSlotName(input)

            if (slot == null) {
                return@CommandHandler "未知的槽位「$input」。\n可选：武器、防具、头盔、鞋子、手套、戒指、项链、副手"
            }

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

            // 匹配敌人（支持编号）
            val enemyId: String
            if (args.isEmpty()) {
                if (availableEnemies.size == 1) {
                    enemyId = availableEnemies.first()
                } else {
                    val list = availableEnemies.mapIndexed { i, id ->
                        val name = combatConfigResult.enemies[id]?.displayName ?: id
                        "  ${i + 1}. $name"
                    }.joinToString("\n")
                    return@CommandHandler "这里有多个敌人，请指定目标：\n$list\n用法：/战斗 <编号 或 敌人名>"
                }
            } else {
                val input = args.joinToString(" ")
                enemyId = pickFromList(input, availableEnemies) { id, inp ->
                    id == inp
                        || combatConfigResult.enemies[id]?.displayName == inp
                        || combatConfigResult.enemies[id]?.displayName?.contains(inp) == true
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
                    return@CommandHandler generateNpcDialogue(llmClient, aiConfig, npc.displayName, npc.prompt, "你好")
                }
                val list = npcsHere.mapIndexed { i, n -> "  ${i + 1}. ${n.displayName}" }.joinToString("\n")
                return@CommandHandler "这里有：\n$list\n用法：/对话 <编号 或 NPC名> [想说的话]"
            }

            val first = args.first()

            // 1) 纯编号：/对话 2 或 /对话 2 你好
            val asIndex = first.toIntOrNull()
            val npcMatch: org.textrpg.application.domain.model.NpcDefinition?
            val playerMessage: String
            if (asIndex != null) {
                npcMatch = npcsHere.getOrNull(asIndex - 1)
                    ?: return@CommandHandler "编号 $asIndex 超出范围（共 ${npcsHere.size} 位）。"
                playerMessage = args.drop(1).joinToString(" ").ifEmpty { "你好" }
            } else {
                // 2) 名字前缀匹配（原逻辑）：优先完整名前缀
                val input = args.joinToString(" ")
                val prefixMatch = npcsHere.find { input.startsWith(it.displayName) }
                if (prefixMatch != null) {
                    npcMatch = prefixMatch
                    playerMessage = input.removePrefix(prefixMatch.displayName).trim().ifEmpty { "你好" }
                } else {
                    val fuzzy = npcsHere.find { it.displayName.contains(first) || it.key == first }
                        ?: return@CommandHandler "这里没有「$first」。"
                    npcMatch = fuzzy
                    playerMessage = args.drop(1).joinToString(" ").ifEmpty { "你好" }
                }
            }

            questManager.onEvent(QuestEvent.NpcTalked(ctx.playerId, npcMatch.key))
            generateNpcDialogue(llmClient, aiConfig, npcMatch.displayName, npcMatch.prompt, playerMessage)
        })

        // ==================== /任务 ====================
        registry.registerBuiltin("quest", CommandHandler { args, context ->
            val ctx = context as PlayerContext
            val action = args.firstOrNull() ?: ""

            // 获取当前节点可接任务列表（跨所有 handler 复用）
            fun getAvailableQuestIds(): List<String> {
                val locationId = ctx.mapManager.getPlayerLocation(ctx.playerId) ?: return emptyList()
                val npcsHere = npcManager.getNpcsAtNode(locationId)
                return npcsHere.flatMap { npc ->
                    npc.functions
                        .filter { it.type == "quest_giver" }
                        .flatMap { func ->
                            func.params["quest_ids"]?.split(",")?.map { it.trim() } ?: emptyList()
                        }
                }.filter { qid ->
                    val status = questManager.getQuestStatus(ctx.playerId, qid)
                    status == null || status == QuestStatus.TURNED_IN
                }.distinct()
            }

            when (action) {
                "接取", "accept" -> {
                    val available = getAvailableQuestIds()
                    if (available.isEmpty()) {
                        return@CommandHandler "这里没有可接取的任务。"
                    }
                    val indexArg = args.getOrNull(1)
                    val targetQid: String = if (indexArg == null) {
                        // 无编号：接第一个
                        available.first()
                    } else {
                        pickFromList(indexArg, available) { qid, inp ->
                            val name = questConfig.quests[qid]?.displayName
                            qid == inp || name == inp || name?.contains(inp) == true
                        } ?: return@CommandHandler "没有找到「$indexArg」对应的任务。"
                    }
                    val result = questManager.acceptQuest(ctx.playerId, targetQid, ctx)
                    result.message ?: "操作完成。"
                }
                "交付", "turnin" -> {
                    val activeQuests = questManager.getActiveQuests(ctx.playerId)
                    val completedList = activeQuests.filter { it.status == QuestStatus.COMPLETED }
                    if (completedList.isEmpty()) {
                        return@CommandHandler "没有可交付的任务。"
                    }
                    val indexArg = args.getOrNull(1)
                    val targetProgress = if (indexArg == null) {
                        completedList.first()
                    } else {
                        pickFromList(indexArg, completedList) { prog, inp ->
                            val name = questConfig.quests[prog.questId]?.displayName
                            prog.questId == inp || name == inp || name?.contains(inp) == true
                        } ?: return@CommandHandler "没有找到「$indexArg」对应的已完成任务。"
                    }

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
                    val result = questManager.turnInQuest(ctx.playerId, targetProgress.questId, accessor)
                    result.message ?: "操作完成。"
                }
                else -> {
                    // 默认：进行中 + 当前节点可接 + 可交付
                    val activeQuests = questManager.getActiveQuests(ctx.playerId)
                    val available = getAvailableQuestIds()
                    val completed = activeQuests.filter { it.status == QuestStatus.COMPLETED }

                    buildString {
                        appendLine("━━━ 任务 ━━━")

                        // 进行中
                        val ongoing = activeQuests.filter { it.status == QuestStatus.ACTIVE }
                        if (ongoing.isNotEmpty()) {
                            appendLine("〖进行中〗")
                            for (progress in ongoing) {
                                val def = questConfig.quests[progress.questId]
                                val name = def?.displayName ?: progress.questId
                                appendLine("  · $name")
                                if (def != null) {
                                    for ((i, obj) in def.objectives.withIndex()) {
                                        val op = progress.objectives.getOrNull(i)
                                        val cur = op?.current ?: 0
                                        val req = op?.required ?: obj.quantity
                                        appendLine("      - ${obj.type.value}: $cur / $req")
                                    }
                                }
                            }
                        }

                        // 可交付
                        if (completed.isNotEmpty()) {
                            appendLine()
                            appendLine("〖可交付〗")
                            completed.forEachIndexed { i, progress ->
                                val name = questConfig.quests[progress.questId]?.displayName ?: progress.questId
                                appendLine("  ${i + 1}. $name")
                            }
                            appendLine("  用法：/任务 交付 <编号>")
                        }

                        // 当前节点可接
                        if (available.isNotEmpty()) {
                            appendLine()
                            appendLine("〖此处可接〗")
                            available.forEachIndexed { i, qid ->
                                val name = questConfig.quests[qid]?.displayName ?: qid
                                appendLine("  ${i + 1}. $name")
                            }
                            appendLine("  用法：/任务 接取 <编号>")
                        }

                        if (ongoing.isEmpty() && completed.isEmpty() && available.isEmpty()) {
                            appendLine("（当前没有进行中或可接取的任务）")
                            append("提示：与 NPC 所在区域交谈，或查看地图节点的可接任务提示。")
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
                        return@CommandHandler "请指定物品。\n用法：/商店 买 <编号 或 物品名>"
                    }
                    val template = pickFromList(itemName, shopItems) { tmpl, inp ->
                        tmpl.name == inp || tmpl.name.contains(inp)
                    } ?: return@CommandHandler "商店没有「$itemName」。"

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
                        return@CommandHandler "请指定物品。\n用法：/商店 卖 <编号 或 物品名>"
                    }
                    val invItems = itemRepository.findByPlayerId(ctx.playerId)
                        .filter { it.slotType == org.textrpg.application.domain.model.SlotType.INVENTORY }
                    val matched = pickFromList(itemName, invItems) { item, inp ->
                        val tmpl = allTemplates[item.templateId]
                        tmpl?.name == inp || tmpl?.name?.contains(inp) == true
                    } ?: return@CommandHandler "背包中没有「$itemName」。"
                    val template = allTemplates[matched.templateId] ?: return@CommandHandler "物品数据异常。"
                    val sellPrice = template.price / 2
                    itemRepository.delete(matched.id)
                    ctx.modifyAttribute("gold", sellPrice.toDouble())
                    "卖出了「${template.name}」，获得 $sellPrice 金币。"
                }
                else -> {
                    // 默认：显示商店（带编号）
                    val invItems = itemRepository.findByPlayerId(ctx.playerId)
                        .filter { it.slotType == org.textrpg.application.domain.model.SlotType.INVENTORY }
                    buildString {
                        appendLine("━━━ ${shopNpc.displayName}的商店 ━━━")
                        val gold = ctx.getAttributeValue("gold")?.toInt() ?: 0
                        appendLine("你的金币：$gold")
                        if (shopItems.isNotEmpty()) {
                            appendLine()
                            appendLine("〖售卖〗")
                            shopItems.forEachIndexed { i, item ->
                                appendLine("  ${i + 1}. ${item.name}  ${item.price} 金币")
                                if (item.description.isNotBlank()) appendLine("     ${item.description}")
                            }
                        }
                        if (invItems.isNotEmpty()) {
                            appendLine()
                            appendLine("〖你的背包（半价回收）〗")
                            invItems.forEachIndexed { i, inv ->
                                val tmpl = allTemplates[inv.templateId]
                                val name = tmpl?.name ?: "未知"
                                val sellPrice = (tmpl?.price ?: 0) / 2
                                val qty = if (inv.quantity > 1) " x${inv.quantity}" else ""
                                appendLine("  ${i + 1}. $name$qty  ${sellPrice} 金币")
                            }
                        }
                        appendLine()
                        append("用法：/商店 买 <编号 或 名字>  |  /商店 卖 <编号 或 名字>")
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

            ctx.reply("正在探索未知区域……")

            // 已存在的节点名列表，给 AI 避重
            val existingNodeNames = mapManager.getAllNodeIds()
                .mapNotNull { mapManager.getNode(it)?.displayName }
                .filter { it.isNotBlank() }
                .distinct()

            // 敌人候选池（让 AI 从中挑选，避免引入未定义敌人）
            val enemyCatalog = combatConfigResult.enemies.map { (id, def) ->
                "$id (${def.displayName})"
            }

            val scenePrompt = aiConfig.scenes["world_generation"]?.promptTemplate ?: ""
            val contextInfo = buildString {
                appendLine("当前位置：${currentNode.displayName}（${currentNode.description}）")
                appendLine()
                appendLine("已存在的区域名称（必须避开，生成与这些完全不同的新区域）：")
                appendLine(existingNodeNames.joinToString("、"))
                appendLine()
                appendLine("可用的敌人 ID 清单（enemies 字段必须从中选 1~2 个，不要编造新 ID）：")
                appendLine(enemyCatalog.joinToString("、"))
            }

            val messages = listOf(
                LLMMessage.System(scenePrompt),
                LLMMessage.User(contextInfo)
            )

            val result = llmClient.generate(messages)
            if (result.isFailure) {
                return@CommandHandler "探索失败……迷雾太浓了，什么都看不清。"
            }

            val responseText = result.getOrDefault("")
            val validEnemyIds = combatConfigResult.enemies.keys
            val newNode = parseGeneratedNode(responseText, locationId, validEnemyIds, existingNodeNames)
            if (newNode != null) {
                mapManager.addNode(newNode)
                val updatedCurrentNode = currentNode.copy(
                    connections = currentNode.connections + NodeConnection(
                        target = newNode.key,
                        display = "→ ${newNode.displayName}"
                    )
                )
                mapManager.addNode(updatedCurrentNode)
                mapManager.setPlayerLocation(ctx.playerId, newNode.key)
                questManager.onEvent(QuestEvent.NodeReached(ctx.playerId, newNode.key))

                // 进入新区域后直接展示 rich info
                "━━━ 发现新区域 ━━━\n" + buildRichNodeInfo(ctx)
            } else {
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
     *
     * @param responseText AI 原始回复
     * @param parentNodeId 来源节点 key（用于双向连接的返回边）
     * @param validEnemyIds 允许出现的敌人 ID 集合（过滤掉 AI 编造的未定义敌人）
     * @param existingNodeNames 已存在的节点显示名（若 AI 还是生成了重名，返回 null 让调用方重试/降级）
     */
    private fun parseGeneratedNode(
        responseText: String,
        parentNodeId: String,
        validEnemyIds: Set<String>,
        existingNodeNames: List<String>
    ): MapNodeDefinition? {
        return try {
            val jsonStr = responseText
                .replace("```json", "").replace("```", "")
                .trim()
            val gson = com.google.gson.Gson()
            val json = gson.fromJson(jsonStr, com.google.gson.JsonObject::class.java)

            val name = json.get("name")?.asString ?: return null
            val description = json.get("description")?.asString ?: ""
            val tags = json.getAsJsonArray("tags")?.map { it.asString }?.toSet() ?: emptySet()

            // 避重：和已存在节点名重复则失败
            if (existingNodeNames.any { it.equals(name.trim(), ignoreCase = true) }) {
                println("Warning: AI 生成的节点名与已存在节点重复：$name")
                return null
            }

            // 过滤敌人：仅保留白名单内的 ID
            val enemies = json.getAsJsonArray("enemies")
                ?.mapNotNull { it.asString }
                ?.filter { it in validEnemyIds }
                ?: emptyList()

            // 生成唯一 key（加随机后缀避免同毫秒碰撞）
            val key = "gen_${System.currentTimeMillis()}_${(0..999).random()}"

            MapNodeDefinition(
                key = key,
                displayName = name,
                description = description,
                tags = tags,
                connections = listOf(NodeConnection(target = parentNodeId, display = "→ 返回")),
                entities = NodeEntities(enemies = enemies),
                isBoundary = true
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

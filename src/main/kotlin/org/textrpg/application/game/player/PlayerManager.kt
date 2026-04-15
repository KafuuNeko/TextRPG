package org.textrpg.application.game.player

import org.textrpg.application.data.repository.Player
import org.textrpg.application.data.repository.PlayerRepository
import org.textrpg.application.domain.model.AttributeDefinition
import org.textrpg.application.domain.model.BuffDefinition
import org.textrpg.application.game.attribute.AttributeContainer
import org.textrpg.application.game.buff.BuffManager
import org.textrpg.application.game.command.SessionManager
import org.textrpg.application.game.inventory.InventoryService
import org.textrpg.application.game.map.MapManager
import java.util.concurrent.ConcurrentHashMap

/**
 * 玩家生命周期管理器
 *
 * 管理在线玩家的内存状态缓存（AttributeContainer + BuffManager），
 * 并在适当时机持久化到数据库。
 *
 * 每次消息处理时，调用 [getContext] 获取当前玩家的 [PlayerContext]。
 * 框架在内存中维护活跃玩家的属性状态，仅在安全节点（战斗结束、地图切换、下线）落盘。
 *
 * @param playerRepository  玩家仓储
 * @param attributeDefinitions 属性定义（从 YAML 加载）
 * @param buffDefinitions   Buff 定义（Phase 3 接入）
 * @param sessionManager    会话管理器
 * @param mapManager        地图管理器
 * @param inventoryService  背包服务
 */
class PlayerManager(
    private val playerRepository: PlayerRepository,
    private val attributeDefinitions: Map<String, AttributeDefinition>,
    private val buffDefinitions: Map<String, BuffDefinition>,
    private val sessionManager: SessionManager,
    private val mapManager: MapManager,
    private val inventoryService: InventoryService
) {

    /**
     * 在线玩家缓存数据
     *
     * 缓存属性容器和 Buff 管理器，避免每次消息都从数据库加载。
     */
    data class CachedPlayerData(
        val playerId: Long,
        val bindAccount: String,
        val attributeContainer: AttributeContainer,
        val buffManager: BuffManager
    )

    /** 在线玩家缓存（bindAccount → CachedPlayerData） */
    private val cache = ConcurrentHashMap<String, CachedPlayerData>()

    /**
     * 获取玩家上下文
     *
     * 按优先级查找：内存缓存 → 数据库 → 返回未注册上下文。
     * 缓存命中时复用 AttributeContainer（有状态），每次重建轻量级 PlayerContext 包装。
     *
     * @param bindAccount 社交平台账号 ID
     * @param replier     当前消息的回复函数
     * @return 玩家上下文
     */
    fun getContext(bindAccount: String, replier: suspend (String) -> Unit): PlayerContext {
        // 1. 命中缓存 → 复用已有属性状态
        cache[bindAccount]?.let { cached ->
            return buildContext(cached, replier)
        }

        // 2. 数据库查询 → 加载并缓存
        val player = playerRepository.findByBindAccount(bindAccount)
        if (player != null) {
            val cached = loadAndCache(player)
            return buildContext(cached, replier)
        }

        // 3. 未注册 → 返回访客上下文
        return PlayerContext(
            playerId = 0L,
            isRegistered = false,
            bindAccount = bindAccount,
            attributeContainer = null,
            buffManager = null,
            sessionManager = sessionManager,
            mapManager = mapManager,
            inventoryService = inventoryService,
            replier = replier
        )
    }

    /**
     * 注册新角色
     *
     * 创建数据库记录、初始化属性（使用 YAML 中的 defaultValue），
     * 将初始属性快照落盘，设置初始地图位置，并刷新缓存。
     *
     * @param name        角色名
     * @param bindAccount 绑定的社交平台账号 ID
     * @return 新建的玩家领域模型
     */
    fun registerPlayer(name: String, bindAccount: String): Player {
        // 创建数据库记录
        val player = playerRepository.save(
            Player(name = name, bindAccount = bindAccount)
        )

        // 初始化属性容器（所有属性取 YAML 中的 defaultValue）
        val attributes = AttributeContainer(attributeDefinitions)
        initResourceAttributes(attributes)

        // 持久化初始属性快照
        playerRepository.saveAttributeData(player.id, attributes.serializeBaseValues())

        // 设置初始位置：村庄广场
        mapManager.setPlayerLocation(player.id, "village_square")

        // 写入缓存
        val buffManager = BuffManager(buffDefinitions, attributes)
        cache[bindAccount] = CachedPlayerData(player.id, bindAccount, attributes, buffManager)

        return player
    }

    /**
     * 给玩家添加初始物品
     *
     * 注册后调用，赠送新手装备和消耗品。
     * 分离出来是因为需要 ItemSeeder 先完成模板播种。
     *
     * @param playerId 玩家 ID
     * @param startingItems 初始物品列表（templateId → quantity）
     */
    fun giveStartingItems(playerId: Long, startingItems: Map<Int, Int>) {
        for ((templateId, quantity) in startingItems) {
            inventoryService.addItem(playerId, templateId, quantity)
        }
    }

    /**
     * 检查角色名是否已被占用
     */
    fun isNameTaken(name: String): Boolean {
        return playerRepository.findByName(name) != null
    }

    /**
     * 保存指定玩家的属性到数据库
     */
    fun savePlayer(bindAccount: String) {
        val cached = cache[bindAccount] ?: return
        playerRepository.saveAttributeData(cached.playerId, cached.attributeContainer.serializeBaseValues())
    }

    /**
     * 保存所有在线玩家的属性
     */
    fun saveAll() {
        cache.forEach { (account, _) -> savePlayer(account) }
    }

    /**
     * 移除在线玩家（下线时调用：先存盘再移除缓存）
     */
    fun removePlayer(bindAccount: String) {
        savePlayer(bindAccount)
        cache.remove(bindAccount)
    }

    /** 当前在线玩家数量 */
    fun onlineCount(): Int = cache.size

    // ======================== 内部方法 ========================

    private fun loadAndCache(player: Player): CachedPlayerData {
        val attributes = AttributeContainer(attributeDefinitions)
        if (player.attributeData.isNotBlank() && player.attributeData != "{}") {
            attributes.deserializeBaseValues(player.attributeData)
        }
        val buffManager = BuffManager(buffDefinitions, attributes)
        val cached = CachedPlayerData(player.id, player.bindAccount, attributes, buffManager)
        cache[player.bindAccount] = cached

        // 确保玩家有地图位置（DB 恢复后可能丢失内存位置）
        if (mapManager.getPlayerLocation(player.id) == null) {
            mapManager.setPlayerLocation(player.id, "village_square")
        }

        return cached
    }

    private fun buildContext(cached: CachedPlayerData, replier: suspend (String) -> Unit): PlayerContext {
        return PlayerContext(
            playerId = cached.playerId,
            isRegistered = true,
            bindAccount = cached.bindAccount,
            attributeContainer = cached.attributeContainer,
            buffManager = cached.buffManager,
            sessionManager = sessionManager,
            mapManager = mapManager,
            inventoryService = inventoryService,
            replier = replier
        )
    }

    /**
     * 初始化资源属性（current_hp = max_hp, current_mp = max_mp）
     */
    private fun initResourceAttributes(attributes: AttributeContainer) {
        val maxHp = attributes.getValue("max_hp")
        if (maxHp > 0) attributes.setBaseValue("current_hp", maxHp)

        val maxMp = attributes.getValue("max_mp")
        if (maxMp > 0) attributes.setBaseValue("current_mp", maxMp)
    }
}

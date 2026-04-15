package org.textrpg.application.game.player

import org.textrpg.application.game.attribute.AttributeContainer
import org.textrpg.application.game.buff.BuffManager
import org.textrpg.application.game.command.CommandContext
import org.textrpg.application.game.command.SessionManager
import org.textrpg.application.game.map.MapManager

/**
 * 范例层的 CommandContext 实现（胶水层）
 *
 * 将框架层的各子系统串联在一起，为指令路由提供统一的游戏状态访问。
 * 每次消息处理时由 [PlayerManager] 构建，绑定到单个玩家的消息上下文。
 *
 * 对于未注册的玩家（[isRegistered] = false），属性 / Buff / 物品查询均返回空值，
 * 仅允许执行不需要 registered 前置条件的指令（如 /注册）。
 *
 * @param playerId     数据库主键（未注册时为 0）
 * @param isRegistered 是否已注册角色
 * @param bindAccount  绑定的社交平台账号 ID
 * @param attributeContainer 属性容器（未注册时为 null）
 * @param buffManager  Buff 管理器（未注册时为 null）
 * @param sessionManager 会话管理器
 * @param mapManager   地图管理器（可选，Phase 2 接入）
 * @param replier      当前消息的回复函数
 */
class PlayerContext(
    override val playerId: Long,
    override val isRegistered: Boolean,
    val bindAccount: String,
    val attributeContainer: AttributeContainer?,
    val buffManager: BuffManager?,
    private val sessionManager: SessionManager,
    private val mapManager: MapManager?,
    private val replier: suspend (String) -> Unit
) : CommandContext {

    override fun getAttributeValue(key: String): Double? {
        val container = attributeContainer ?: return null
        if (!container.contains(key)) return null
        return container.getValue(key)
    }

    override fun modifyAttribute(key: String, delta: Double) {
        attributeContainer?.modifyBaseValue(key, delta)
    }

    override val currentSessionType: String?
        get() = if (playerId > 0) sessionManager.getSessionType(playerId) else null

    override fun getCurrentNodeTags(): Set<String> {
        if (playerId <= 0) return emptySet()
        return mapManager?.getNodeTags(playerId) ?: emptySet()
    }

    override fun hasItem(itemId: String, quantity: Int): Boolean {
        // Phase 2 接入 InventoryService
        return false
    }

    override fun hasBuff(buffId: String): Boolean {
        return buffManager?.hasBuff(buffId) ?: false
    }

    override suspend fun reply(message: String) {
        replier(message)
    }
}

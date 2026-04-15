package org.textrpg.application.game.skill

import org.textrpg.application.game.command.CommandContext
import org.textrpg.application.game.effect.EntityAccessor

/**
 * 技能上下文适配器
 *
 * 将 [EntityAccessor] 桥接为 [CommandContext]，使 [RequiresChecker] 可以直接复用于技能前置条件校验。
 *
 * 设计说明：
 * - [CommandContext] 面向单玩家指令场景，包含会话、注册等指令相关信息
 * - [EntityAccessor] 面向特效引擎的实体操作
 * - 技能系统需要同时使用两者：RequiresChecker 需要 CommandContext，EffectEngine 需要 EntityAccessor
 * - 本适配器在不修改两个接口的前提下解决桥接需求
 *
 * @param entity 被适配的实体访问器
 * @param sessionType 当前会话类型（如 "combat"），默认 null
 * @param nodeTags 当前地图节点标签集合，默认空集
 * @param registered 实体是否已注册角色，默认 true（技能使用者通常已注册）
 */
class SkillContextAdapter(
    private val entity: EntityAccessor,
    private val sessionType: String? = null,
    private val nodeTags: Set<String> = emptySet(),
    private val registered: Boolean = true
) : CommandContext {

    override val playerId: Long
        get() = entity.entityId.toLongOrNull() ?: 0L

    override val isRegistered: Boolean
        get() = registered

    override fun getAttributeValue(key: String): Double? {
        // EntityAccessor.getAttributeValue 约定不抛异常（属性不存在返回 0.0），
        // 因此无需 try-catch；签名返回 Double? 是 CommandContext 的接口约束。
        return entity.getAttributeValue(key)
    }

    override fun modifyAttribute(key: String, delta: Double) {
        entity.modifyAttribute(key, delta)
    }

    override val currentSessionType: String?
        get() = sessionType

    override fun getCurrentNodeTags(): Set<String> = nodeTags

    override fun hasItem(itemId: String, quantity: Int): Boolean {
        return entity.hasItem(itemId, quantity)
    }

    override fun hasBuff(buffId: String): Boolean {
        return entity.hasBuff(buffId)
    }

    override suspend fun reply(message: String) {
        entity.sendMessage(message)
    }
}

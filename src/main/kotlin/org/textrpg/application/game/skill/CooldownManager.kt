package org.textrpg.application.game.skill

import java.util.concurrent.ConcurrentHashMap

/**
 * 技能冷却管理器
 *
 * 管理所有实体的技能冷却状态。冷却基于**回合制**——每回合结束时调用 [tickCooldowns] 递减。
 * 使用 [ConcurrentHashMap] 保证线程安全，支持多协程并发访问。
 *
 * 数据结构：`entityId → (skillId → 剩余回合数)`
 *
 * 使用示例：
 * ```kotlin
 * // 使用技能后记录冷却
 * cooldownManager.recordCooldown("player_123", "fireball", 3)
 *
 * // 回合结束时递减所有冷却
 * cooldownManager.tickCooldowns("player_123")
 *
 * // 使用技能前检查冷却
 * if (cooldownManager.isOnCooldown("player_123", "fireball")) {
 *     val remaining = cooldownManager.getCooldown("player_123", "fireball")
 *     // 提示玩家还需等待 remaining 回合
 * }
 * ```
 */
class CooldownManager {

    /** entityId → (skillId → 剩余回合数) */
    private val cooldowns = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    /**
     * 检查技能是否在冷却中
     *
     * @param entityId 实体标识符
     * @param skillId 技能标识符
     * @return 是否仍在冷却中
     */
    fun isOnCooldown(entityId: String, skillId: String): Boolean {
        return getCooldown(entityId, skillId) > 0
    }

    /**
     * 获取技能剩余冷却回合数
     *
     * @param entityId 实体标识符
     * @param skillId 技能标识符
     * @return 剩余回合数，不在冷却中返回 0
     */
    fun getCooldown(entityId: String, skillId: String): Int {
        return cooldowns[entityId]?.get(skillId) ?: 0
    }

    /**
     * 记录技能冷却
     *
     * 技能使用成功后调用，设置冷却回合数。
     *
     * @param entityId 实体标识符
     * @param skillId 技能标识符
     * @param rounds 冷却回合数
     */
    fun recordCooldown(entityId: String, skillId: String, rounds: Int) {
        if (rounds <= 0) return
        cooldowns.getOrPut(entityId) { ConcurrentHashMap() }[skillId] = rounds
    }

    /**
     * 递减指定实体的所有技能冷却
     *
     * 每回合结束时调用。冷却降至 0 的技能自动移除。
     *
     * @param entityId 实体标识符
     */
    fun tickCooldowns(entityId: String) {
        val entityCooldowns = cooldowns[entityId] ?: return
        val expired = mutableListOf<String>()

        entityCooldowns.forEach { (skillId, remaining) ->
            val newValue = remaining - 1
            if (newValue <= 0) {
                expired.add(skillId)
            } else {
                entityCooldowns[skillId] = newValue
            }
        }

        expired.forEach { entityCooldowns.remove(it) }

        // 清理空映射
        if (entityCooldowns.isEmpty()) {
            cooldowns.remove(entityId)
        }
    }

    /**
     * 递减所有实体的技能冷却
     *
     * 全局回合结束时的便捷方法。
     */
    fun tickAllCooldowns() {
        cooldowns.keys.toList().forEach { tickCooldowns(it) }
    }

    /**
     * 清除指定实体的所有冷却
     *
     * 战斗结束或特殊效果清空冷却时使用。
     *
     * @param entityId 实体标识符
     */
    fun clearCooldowns(entityId: String) {
        cooldowns.remove(entityId)
    }
}

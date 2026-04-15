package org.textrpg.application.game.quest

/**
 * 任务事件
 *
 * 外部系统（战斗结算、背包操作、地图移动、NPC 对话）通过发送事件
 * 通知任务引擎更新进度。[QuestManager.onEvent] 接收并处理这些事件。
 *
 * 使用示例：
 * ```kotlin
 * // 战斗结束后
 * questManager.onEvent(QuestEvent.EnemyKilled(playerId, "goblin"))
 *
 * // 拾取物品后
 * questManager.onEvent(QuestEvent.ItemCollected(playerId, "wild_herb", 3))
 * ```
 */
sealed class QuestEvent {
    /** 所有事件都有一个关联的玩家数据库 ID */
    abstract val playerId: Long

    /** 收集了物品 */
    data class ItemCollected(
        override val playerId: Long,
        val itemId: String,
        val quantity: Int = 1
    ) : QuestEvent()

    /** 击杀了敌人 */
    data class EnemyKilled(
        override val playerId: Long,
        val enemyId: String
    ) : QuestEvent()

    /** 到达了地图节点 */
    data class NodeReached(
        override val playerId: Long,
        val nodeId: String
    ) : QuestEvent()

    /** 与 NPC 对话 */
    data class NpcTalked(
        override val playerId: Long,
        val npcId: String
    ) : QuestEvent()
}

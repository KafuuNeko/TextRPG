package org.textrpg.application.domain.entity

import org.joda.time.DateTime
import org.textrpg.application.domain.model.SlotType

/**
 * 玩家背包条目——玩家持有的物品记录
 *
 * 是玩家与物品的关联核心。通过 [instanceId] 是否为 NULL 区分：
 * - 非 NULL：实例物品（装备），quantity 固定为 1
 * - NULL：堆叠物品（药水/材料），quantity 表示持有数量
 *
 * 遵循规范 §4.2：纯 Kotlin 数据类，无框架注解；数据库映射由
 * [org.textrpg.application.data.dao.PlayerInventoryEntity] 承担。
 *
 * @property id 背包条目主键（自增），新建时传 0
 * @property playerId 所属玩家 ID
 * @property templateId 物品模板 ID
 * @property instanceId 实例 ID。堆叠物品为 NULL；装备为具体实例 ID
 * @property quantity 持有数量。实例物品固定为 1
 * @property slotType 位置类型，参见 [SlotType]
 * @property slotIndex 在格子数组中的索引（用于前端排序）
 * @property isBound 是否绑定（绑定后不可交易）
 * @property createdAt 创建时间
 */
data class PlayerInventoryItem(
    val id: Long = 0,
    val playerId: Long,
    val templateId: Int,
    val instanceId: Long? = null,
    val quantity: Int = 1,
    val slotType: SlotType = SlotType.INVENTORY,
    val slotIndex: Int = 0,
    val isBound: Boolean = false,
    val createdAt: DateTime? = null
) {
    val isStackable: Boolean get() = instanceId == null
}

package org.textrpg.application.domain.model

import org.joda.time.DateTime

/**
 * 物品模板——静态配置
 *
 * 定义游戏中所有物品的公共属性，由 YAML 静态文件加载到内存。
 * 一个模板可生成多个背包条目（堆叠物品）或多个实例（装备）。
 *
 * @property id 模板 ID（文件名），如 "iron_sword"
 * @property name 物品名称
 * @property stackable 是否可堆叠。true=消耗品/材料（共享一行背包），false=装备（每件独立一行）
 * @property attribute 物品属性
 * @property description 物品描述文本
 */
data class ItemTemplate(
    val id: String,
    val name: String,
    val stackable: Boolean = true,
    val attribute: String = "{}",
    val description: String = ""
)

/**
 * 物品实例——仅需要唯一属性的物品
 *
 * 只有装备等拥有个性化属性的物品（强化等级、耐久度、随机词条、宝石孔等）才会生成实例。
 * 堆叠物品（药水、材料）不生成实例，背包中 `instance_id` 为 NULL。
 *
 * @property id 实例主键（自增），新建时由数据库生成
 * @property templateId 关联的模板 ID
 * @property attribute 实例动态属性 JSON
 * @property creatorId 制造者玩家 ID（用于展示"XX制造"）
 * @property createdAt 创建时间
 */
data class ItemInstance(
    val id: Long = 0,
    val templateId: String,
    val attribute: String = "{}",
    val creatorId: Long? = null,
    val createdAt: DateTime? = null
)

/**
 * 玩家背包条目——玩家持有的物品记录
 *
 * 是玩家与物品的关联核心。通过 [instanceId] 是否为 NULL 区分：
 * - 非 NULL：实例物品（装备），quantity 固定为 1
 * - NULL：堆叠物品（药水/材料），quantity 表示持有数量
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
    val templateId: String,
    val instanceId: Long? = null,
    val quantity: Int = 1,
    val slotType: SlotType = SlotType.INVENTORY,
    val slotIndex: Int = 0,
    val isBound: Boolean = false,
    val createdAt: DateTime? = null
) {
    val isStackable: Boolean get() = instanceId == null
}

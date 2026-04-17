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
 * @property type 物品类型，参见 [ItemType]
 * @property subType 物品子类型，仅对装备类型有效，参见 [ItemSubType]
 * @property rarity 物品品质，参见 [ItemRarity]
 * @property stackable 是否可堆叠。true=消耗品/材料（共享一行背包），false=装备（每件独立一行）
 * @property baseStats 基础属性 JSON，如 `{"atk": 10, "def": 5}`
 * @property levelReq 使用等级限制
 * @property price 商店基础售价（金币）
 * @property description 物品描述文本
 */
data class ItemTemplate(
    val id: String,
    val name: String,
    val type: ItemType,
    val subType: ItemSubType = ItemSubType.WEAPON,
    val rarity: ItemRarity = ItemRarity.WHITE,
    val stackable: Boolean = true,
    val baseStats: String = "{}",
    val levelReq: Int = 0,
    val price: Int = 0,
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
 * @property level 当前强化等级
 * @property exp 装备当前经验值（用于升级）
 * @property durability 当前耐久度
 * @property randomStats 洗练/随机生成的附加属性 JSON，如 `[{"key":"crit_rate","value":0.05}]`
 * @property sockets 镶嵌信息 JSON，如 `[2001, 2002, null]`，null 表示空孔
 * @property creatorId 制造者玩家 ID（用于展示"XX制造"）
 * @property createdAt 创建时间
 */
data class ItemInstance(
    val id: Long = 0,
    val templateId: String,
    val level: Int = 0,
    val exp: Long = 0L,
    val durability: Int = 100,
    val randomStats: String = "[]",
    val sockets: String = "[]",
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

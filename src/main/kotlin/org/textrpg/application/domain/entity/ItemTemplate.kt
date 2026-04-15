package org.textrpg.application.domain.entity

import org.textrpg.application.domain.model.ItemRarity
import org.textrpg.application.domain.model.ItemSubType
import org.textrpg.application.domain.model.ItemType

/**
 * 物品模板——静态配置
 *
 * 定义游戏中所有物品的公共属性，在服务器启动时通常载入内存缓存。
 * 一个模板可生成多个背包条目（堆叠物品）或多个实例（装备）。
 *
 * 遵循规范 §4.2：纯 Kotlin 数据类，无框架注解；数据库映射由
 * [org.textrpg.application.data.dao.ItemTemplateEntity] 承担。
 *
 * @property id 模板主键（自增），新建时传 0
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
    val id: Int = 0,
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

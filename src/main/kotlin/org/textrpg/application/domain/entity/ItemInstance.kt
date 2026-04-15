package org.textrpg.application.domain.entity

import org.joda.time.DateTime

/**
 * 物品实例——仅需要唯一属性的物品
 *
 * 只有装备等拥有个性化属性的物品（强化等级、耐久度、随机词条、宝石孔等）才会生成实例。
 * 堆叠物品（药水、材料）不生成实例，背包中 `instance_id` 为 NULL。
 *
 * 遵循规范 §4.2：纯 Kotlin 数据类，无框架注解；数据库映射由
 * [org.textrpg.application.data.dao.ItemInstanceEntity] 承担。
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
    val templateId: Int,
    val level: Int = 0,
    val exp: Long = 0L,
    val durability: Int = 100,
    val randomStats: String = "[]",
    val sockets: String = "[]",
    val creatorId: Long? = null,
    val createdAt: DateTime? = null
)

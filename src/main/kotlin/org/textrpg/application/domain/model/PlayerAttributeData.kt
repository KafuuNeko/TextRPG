package org.textrpg.application.domain.model

/**
 * 玩家基础属性配置信息
 *
 * @property name 属性中文名称
 * @property defaultValue 属性默认值
 */
data class BasicAttributeInfo(
    val name: String,
    val defaultValue: Double
)

/**
 * 玩家拓展属性配置信息
 *
 * @property name 属性中文名称
 * @property expression 计算表达式
 */
data class ExtendedAttributeInfo(
    val name: String,
    val expression: String
)

/**
 * 玩家基础属性（存储在数据库中的数值）
 *
 * @property id 关联的 Player ID
 * @property name 属性键（如 "str", "hp_max"）
 * @property value 当前属性值
 */
data class PlayerAttribute(
    val id: Long,
    val name: String,
    val value: Double
)

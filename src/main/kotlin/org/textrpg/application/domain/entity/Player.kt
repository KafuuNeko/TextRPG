package org.textrpg.application.domain.entity

import org.joda.time.DateTime

/**
 * 玩家领域模型
 *
 * 包含玩家基础信息和属性数据快照。遵循规范 §4.2：纯 Kotlin 数据类，
 * 无框架注解、不依赖持久化层。数据库映射由 [org.textrpg.application.data.dao.PlayerEntity] 承担。
 *
 * @property id 数据库主键，新建时传 0
 * @property name 玩家名称（全局唯一）
 * @property bindAccount 绑定的社交平台账号 ID（用于将 QQ/微信等平台用户关联到游戏角色）
 * @property attributeData 属性基础值 JSON 快照（如 `{"strength": 15, "current_hp": 80}`）
 * @property createdAt 创建时间
 * @property updatedAt 更新时间
 */
data class Player(
    val id: Long = 0,
    val name: String,
    val bindAccount: String,
    val attributeData: String = "{}",
    val createdAt: DateTime? = null,
    val updatedAt: DateTime? = null
)

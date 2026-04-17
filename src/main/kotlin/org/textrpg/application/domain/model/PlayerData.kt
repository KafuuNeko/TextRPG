package org.textrpg.application.domain.model

import org.joda.time.DateTime

/**
 * 玩家领域模型
 *
 * 仅包含玩家基础信息，不包含游戏数值（等级、经验、生命值等）。
 * 游戏数值由独立的数值服务或领域模块管理。
 *
 * @property id 数据库主键，新建时传 0
 * @property name 玩家名称（全局唯一）
 * @property bindAccount 绑定的社交平台账号 ID（用于将 QQ/微信等平台用户关联到游戏角色）
 * @property createdAt 创建时间
 * @property updatedAt 更新时间
 */
data class Player(
    val id: Long = 0,
    val name: String,
    val bindAccount: String,
    val createdAt: DateTime? = null,
    val updatedAt: DateTime? = null
)

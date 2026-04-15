package org.textrpg.application.domain.entity

import org.joda.time.DateTime

/**
 * 持久性 Buff 数据模型
 *
 * 存储需要跨会话保留的 Buff 状态（如中毒、祝福等），与战斗内临时 Buff 的区别
 * 在于"下线后仍然保留"。战斗临时 Buff 由 `BuffManager` 内存管理，不走此模型。
 *
 * 遵循规范 §4.2：纯 Kotlin 数据类，无框架注解；数据库映射由
 * [org.textrpg.application.data.dao.PlayerBuffEntity] 承担。
 *
 * @property id 数据库主键，新建时传 0
 * @property playerId 玩家数据库 ID
 * @property buffId Buff 定义标识符
 * @property stacks 当前叠加层数
 * @property remainingDuration 剩余持续回合数（负数表示永久）
 * @property createdAt 施加时间
 */
data class PersistentBuff(
    val id: Long = 0,
    val playerId: Long,
    val buffId: String,
    val stacks: Int = 1,
    val remainingDuration: Int = -1,
    val createdAt: DateTime? = null
)

package org.textrpg.application.domain.entity

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.textrpg.application.data.database.PlayerBuffs

/**
 * 玩家持久性 Buff Exposed DAO 实体
 *
 * 对应数据库 `player_buffs` 表。
 * 存储需要跨会话保留的 Buff 状态（如中毒、祝福等）。
 */
class PlayerBuffEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PlayerBuffEntity>(PlayerBuffs)

    var playerId by PlayerBuffs.playerId
    var buffId by PlayerBuffs.buffId
    var stacks by PlayerBuffs.stacks
    var remainingDuration by PlayerBuffs.remainingDuration
    var createdAt by PlayerBuffs.createdAt
}

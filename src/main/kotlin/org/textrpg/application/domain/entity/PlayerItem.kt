package org.textrpg.application.domain.entity

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.textrpg.application.data.database.PlayerItems

/**
 * 玩家物品条目 Exposed DAO 实体
 *
 * 对应数据库 `player_items` 表，是玩家与物品的关联核心。
 * 通过 [instanceId] 是否为 NULL 区分堆叠物品和实例物品。
 *
 * @param id EntityID<Long>，数据库自增主键
 */
class PlayerItemEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PlayerItemEntity>(PlayerItems)

    var playerId by PlayerItems.playerId
    var templateId by PlayerItems.templateId
    var instanceId by PlayerItems.instanceId
    var quantity by PlayerItems.quantity
    var slotType by PlayerItems.slotType
    var slotIndex by PlayerItems.slotIndex
    var isBound by PlayerItems.isBound
    var createdAt by PlayerItems.createdAt
}

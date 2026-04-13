package org.textrpg.application.data.entity

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.textrpg.application.data.database.PlayerInventories

/**
 * 玩家背包条目 Exposed DAO 实体
 *
 * 对应数据库 `player_inventories` 表，是玩家与物品的关联核心。
 * 通过 [instanceId] 是否为 NULL 区分堆叠物品和实例物品。
 *
 * @param id EntityID<Long>，数据库自增主键
 */
class PlayerInventoryEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PlayerInventoryEntity>(PlayerInventories)

    var playerId by PlayerInventories.playerId
    var templateId by PlayerInventories.templateId
    var instanceId by PlayerInventories.instanceId
    var quantity by PlayerInventories.quantity
    var slotType by PlayerInventories.slotType
    var slotIndex by PlayerInventories.slotIndex
    var isBound by PlayerInventories.isBound
    var createdAt by PlayerInventories.createdAt
}
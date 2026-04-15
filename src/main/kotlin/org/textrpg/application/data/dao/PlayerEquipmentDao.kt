package org.textrpg.application.data.dao

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.textrpg.application.data.database.PlayerEquipments

/**
 * 玩家装备 DAO 实体
 *
 * 映射 [PlayerEquipments] 表，记录玩家各装备槽位上的物品实例 ID。
 * 每个玩家最多一行记录（playerId 唯一索引），8 个可空槽位字段。
 * 槽位值为物品实例的主键 ID，null 表示该槽位为空。
 */
class PlayerEquipmentEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PlayerEquipmentEntity>(PlayerEquipments)

    var playerId by PlayerEquipments.playerId
    var slotHead by PlayerEquipments.slotHead
    var slotChest by PlayerEquipments.slotChest
    var slotWeapon by PlayerEquipments.slotWeapon
    var slotOffhand by PlayerEquipments.slotOffhand
    var slotRing by PlayerEquipments.slotRing
    var slotAmulet by PlayerEquipments.slotAmulet
    var slotBoots by PlayerEquipments.slotBoots
    var slotGloves by PlayerEquipments.slotGloves
}

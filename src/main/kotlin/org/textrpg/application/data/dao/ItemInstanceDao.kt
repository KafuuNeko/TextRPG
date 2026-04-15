package org.textrpg.application.data.dao

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.textrpg.application.data.database.ItemInstances

/**
 * 物品实例 Exposed DAO 实体
 *
 * 对应数据库 `item_instances` 表，仅存储需要唯一属性的物品（装备）。
 * 堆叠物品不生成实例。
 *
 * @param id EntityID<Long>，数据库自增主键
 */
class ItemInstanceEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ItemInstanceEntity>(ItemInstances)

    var templateId by ItemInstances.templateId
    var level by ItemInstances.level
    var exp by ItemInstances.exp
    var durability by ItemInstances.durability
    var randomStats by ItemInstances.randomStats
    var sockets by ItemInstances.sockets
    var creatorId by ItemInstances.creatorId
    var createdAt by ItemInstances.createdAt
}

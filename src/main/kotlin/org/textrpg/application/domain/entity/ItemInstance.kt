package org.textrpg.application.domain.entity

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
    var attribute by ItemInstances.attribute
    var creatorId by ItemInstances.creatorId
    var createdAt by ItemInstances.createdAt
}

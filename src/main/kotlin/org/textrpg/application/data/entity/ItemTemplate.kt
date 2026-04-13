package org.textrpg.application.data.entity

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.textrpg.application.data.database.ItemTemplates

/**
 * 物品模板 Exposed DAO 实体
 *
 * 对应数据库 `item_templates` 表，存储物品的静态配置。
 *
 * @param id EntityID<Int>，数据库自增主键
 */
class ItemTemplateEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ItemTemplateEntity>(ItemTemplates)

    var name by ItemTemplates.name
    var type by ItemTemplates.type
    var subType by ItemTemplates.subType
    var rarity by ItemTemplates.rarity
    var stackable by ItemTemplates.stackable
    var baseStats by ItemTemplates.baseStats
    var levelReq by ItemTemplates.levelReq
    var price by ItemTemplates.price
    var description by ItemTemplates.description
}
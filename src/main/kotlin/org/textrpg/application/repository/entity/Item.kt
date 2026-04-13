package org.textrpg.application.repository.entity

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.textrpg.application.repository.database.Items

class ItemEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ItemEntity>(Items)

    var name by Items.name
    var description by Items.description
    var type by Items.type
    var player by PlayerEntity optionalReferencedOn Items.playerId
    var createdAt by Items.createdAt
}

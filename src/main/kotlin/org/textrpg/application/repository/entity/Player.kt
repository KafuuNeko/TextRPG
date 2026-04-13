package org.textrpg.application.repository.entity

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.textrpg.application.repository.database.Players

class PlayerEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PlayerEntity>(Players)

    var name by Players.name
    var bindAccount by Players.bindAccount
    var createdAt by Players.createdAt
    var updatedAt by Players.updatedAt

    val items by ItemEntity optionalReferrersOn org.textrpg.application.repository.database.Items.playerId
}

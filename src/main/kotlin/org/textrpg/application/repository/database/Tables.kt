package org.textrpg.application.repository.database

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.jodatime.datetime

object Players : LongIdTable("players") {
    val name = varchar("name", 64).uniqueIndex()
    val bindAccount = varchar("bind_account", 128)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

object Items : LongIdTable("items") {
    val name = varchar("name", 128)
    val description = text("description").default("")
    val type = varchar("type", 32)
    val playerId = reference("player_id", Players).nullable()
    val createdAt = datetime("created_at")
}

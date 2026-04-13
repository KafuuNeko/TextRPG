package org.textrpg.application.repository

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.textrpg.application.repository.database.Items
import org.textrpg.application.repository.entity.ItemEntity
import org.textrpg.application.repository.entity.PlayerEntity

data class Item(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val type: String,
    val playerId: Long? = null
)

class ItemRepository(private val database: Database) : Repository<Item, Long> {

    override fun findById(id: Long): Item? = transaction(database) {
        ItemEntity.findById(id)?.toItem()
    }

    override fun findAll(): List<Item> = transaction(database) {
        ItemEntity.all().map { it.toItem() }
    }

    fun findByPlayerId(playerId: Long): List<Item> = transaction(database) {
        ItemEntity.find { Items.playerId eq playerId }.map { it.toItem() }
    }

    override fun save(entity: Item): Item = transaction(database) {
        if (entity.id == 0L) {
            ItemEntity.new {
                name = entity.name
                description = entity.description
                type = entity.type
                player = entity.playerId?.let { PlayerEntity.findById(it) }
                createdAt = DateTime.now()
            }.toItem()
        } else {
            val existing = ItemEntity.findById(entity.id)
                ?: error("Item not found: ${entity.id}")
            existing.apply {
                name = entity.name
                description = entity.description
                type = entity.type
                player = entity.playerId?.let { PlayerEntity.findById(it) }
            }.toItem()
        }
    }

    override fun delete(id: Long): Boolean = transaction(database) {
        val entity = ItemEntity.findById(id) ?: return@transaction false
        entity.delete()
        true
    }

    private fun ItemEntity.toItem() = Item(
        id = id.value,
        name = name,
        description = description,
        type = type,
        playerId = player?.id?.value
    )
}

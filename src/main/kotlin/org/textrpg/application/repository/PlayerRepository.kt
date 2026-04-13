package org.textrpg.application.repository

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.textrpg.application.repository.database.Players
import org.textrpg.application.repository.entity.PlayerEntity

data class Player(
    val id: Long = 0,
    val name: String,
    val bindAccount: String,
    val createdAt: DateTime? = null,
    val updatedAt: DateTime? = null
)

class PlayerRepository(private val database: Database) : Repository<Player, Long> {

    override fun findById(id: Long): Player? = transaction(database) {
        PlayerEntity.findById(id)?.toPlayer()
    }

    override fun findAll(): List<Player> = transaction(database) {
        PlayerEntity.all().map { it.toPlayer() }
    }

    fun findByName(name: String): Player? = transaction(database) {
        PlayerEntity.find { Players.name eq name }.firstOrNull()?.toPlayer()
    }

    fun findByBindAccount(bindAccount: String): Player? = transaction(database) {
        PlayerEntity.find { Players.bindAccount eq bindAccount }.firstOrNull()?.toPlayer()
    }

    override fun save(entity: Player): Player = transaction(database) {
        if (entity.id == 0L) {
            val now = DateTime.now()
            PlayerEntity.new {
                name = entity.name
                bindAccount = entity.bindAccount
                createdAt = now
                updatedAt = now
            }.toPlayer()
        } else {
            val existing = PlayerEntity.findById(entity.id)
                ?: error("Player not found: ${entity.id}")
            existing.apply {
                name = entity.name
                bindAccount = entity.bindAccount
                updatedAt = DateTime.now()
            }.toPlayer()
        }
    }

    override fun delete(id: Long): Boolean = transaction(database) {
        val entity = PlayerEntity.findById(id) ?: return@transaction false
        entity.delete()
        true
    }

    private fun PlayerEntity.toPlayer() = Player(
        id = id.value,
        name = name,
        bindAccount = bindAccount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
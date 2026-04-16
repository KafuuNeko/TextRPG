package org.textrpg.application.data.database

import io.github.oshai.kotlinlogging.KLogger
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.textrpg.application.data.config.DatabaseConfig

class DatabaseFactory(private val config: DatabaseConfig, private val logger: KLogger) {

    val database: Database by lazy {
        Database.connect(url = config.url, driver = config.driver)
    }

    fun init() {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(
                Players,
                ItemTemplates,
                ItemInstances,
                PlayerInventories,
                PlayerEquipments,
                PlayerBuffs
            )
        }
        logger.info { "Database initialized: ${config.url}" }
    }
}
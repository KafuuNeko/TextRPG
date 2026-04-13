package org.textrpg.application.data.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

class DatabaseFactory(private val config: org.textrpg.application.config.DatabaseConfig) {

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
                PlayerEquipments
            )
        }
        println("Database initialized: ${config.url}")
    }
}
package org.textrpg.application.repository.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.textrpg.application.config.DatabaseConfig

class DatabaseFactory(private val config: DatabaseConfig) {

    val database: Database by lazy {
        Database.connect(url = config.url, driver = config.driver)
    }

    fun init() {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(Players, Items)
        }
        println("Database initialized: ${config.url}")
    }
}

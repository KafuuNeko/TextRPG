package org.textrpg.application.data.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.textrpg.application.data.config.DatabaseConfig
import java.io.File

class DatabaseFactory(private val mConfig: DatabaseConfig) {

    val mDatabase: Database by lazy {
        Database.connect(url = mConfig.url, driver = mConfig.driver)
    }

    fun init() {
        // 确保数据库文件所在目录存在（SQLite 不会自动创建父目录）
        val dbPath = mConfig.url.removePrefix("jdbc:sqlite:")
        File(dbPath).parentFile?.mkdirs()

        transaction(mDatabase) {
            SchemaUtils.createMissingTablesAndColumns(
                Players,
                ItemInstances,
                PlayerInventories,
            )
        }
        println("Database initialized: ${mConfig.url}")
    }
}

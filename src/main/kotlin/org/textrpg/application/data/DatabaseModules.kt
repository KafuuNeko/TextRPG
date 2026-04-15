package org.textrpg.application.data

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.textrpg.application.data.config.AppConfig
import org.textrpg.application.data.database.DatabaseFactory
import org.textrpg.application.data.repository.BuffRepository
import org.textrpg.application.data.repository.ItemRepository
import org.textrpg.application.data.repository.PlayerRepository

val databaseModule = module {
    single {
        DatabaseFactory(get<AppConfig>().database).also { it.init() }
    }
    single { get<DatabaseFactory>().database }
}

val repositoryModule = module {
    singleOf(::PlayerRepository)
    singleOf(::ItemRepository)
    singleOf(::BuffRepository)
}

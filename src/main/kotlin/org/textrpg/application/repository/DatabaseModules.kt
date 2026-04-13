package org.textrpg.application.repository

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.textrpg.application.config.AppConfig
import org.textrpg.application.repository.database.DatabaseFactory

val databaseModule = module {
    single {
        DatabaseFactory(get<AppConfig>().database).also { it.init() }
    }
    single { get<DatabaseFactory>().database }
}

val repositoryModule = module {
    singleOf(::PlayerRepository)
    singleOf(::ItemRepository)
}

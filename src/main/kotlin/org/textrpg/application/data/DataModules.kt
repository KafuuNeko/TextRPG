package org.textrpg.application.data

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.textrpg.application.data.config.AppConfig
import org.textrpg.application.data.config.ConfigLoader
import org.textrpg.application.data.config.PlayerAttributeConfig
import org.textrpg.application.data.config.PlayerAttributeConfigLoader
import org.textrpg.application.data.database.DatabaseFactory
import org.textrpg.application.data.repository.ItemRepository
import org.textrpg.application.data.repository.MapRepository
import org.textrpg.application.data.repository.PlayerRepository
import org.textrpg.application.data.repository.PlayerAttributeRepository
import org.textrpg.application.data.registry.ItemTemplateRegistry
import org.textrpg.application.data.registry.MapConnectionRegistry
import org.textrpg.application.data.registry.MapTemplateRegistry
import org.textrpg.application.data.manager.ItemManager
import org.textrpg.application.data.manager.MapManager
import org.textrpg.application.data.manager.PlayerManager

val configModules = module {
    single<AppConfig> {
        ConfigLoader(get()).loadOrDefault()
    }

    single<PlayerAttributeConfig> {
        PlayerAttributeConfigLoader(get()).loadOrDefault()
    }
}

val databaseModule = module {
    single {
        DatabaseFactory(get<AppConfig>().database).also { it.init() }
    }
    single { get<DatabaseFactory>().mDatabase }
}

val repositoryModule = module {
    singleOf(::PlayerRepository)
    singleOf(::PlayerAttributeRepository)
    singleOf(::ItemRepository)
    singleOf(::MapRepository)
}

val staticDataModule = module {
    singleOf(::ItemTemplateRegistry)
    singleOf(::MapConnectionRegistry)
    singleOf(::MapTemplateRegistry)
}

val managerModule = module {
    singleOf(::ItemManager)
    singleOf(::MapManager)
    singleOf(::PlayerManager)
}

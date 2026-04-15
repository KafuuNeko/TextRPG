package org.textrpg.application.data

import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.textrpg.application.data.config.AIConfig
import org.textrpg.application.data.config.AIConfigLoader
import org.textrpg.application.data.config.AttributeConfigLoader
import org.textrpg.application.data.config.BuffConfig
import org.textrpg.application.data.config.BuffConfigLoader
import org.textrpg.application.data.config.CombatConfigLoader
import org.textrpg.application.data.config.CombatConfigResult
import org.textrpg.application.data.config.CommandConfig
import org.textrpg.application.data.config.CommandConfigLoader
import org.textrpg.application.data.config.InventoryConfig
import org.textrpg.application.data.config.InventoryConfigLoader
import org.textrpg.application.data.config.MapConfig
import org.textrpg.application.data.config.MapConfigLoader
import org.textrpg.application.data.config.NpcConfig
import org.textrpg.application.data.config.NpcConfigLoader
import org.textrpg.application.data.config.QuestConfig
import org.textrpg.application.data.config.QuestConfigLoader
import org.textrpg.application.data.config.SkillConfig
import org.textrpg.application.data.config.SkillConfigLoader
import org.textrpg.application.domain.model.AttributeDefinition
import org.textrpg.application.domain.model.BuffDefinition
import org.textrpg.application.domain.model.CombatConfig
import org.textrpg.application.domain.model.EnemyDefinition
import org.textrpg.application.domain.model.QuestDefinition

/**
 * 配置 Koin 模块 Qualifier 常量
 *
 * Kotlin `typealias` 在运行时被擦除为原始类型，因此无法用 typealias 区分
 * Koin 中多个 `Map<String, *>` 单例。所有形如 `Map<String, XxxDefinition>` 的
 * 注册统一用此处命名限定。
 *
 * 仅当 Map 类型有**真实下游消费方**时才追加 qualifier；其他配置消费 data class
 * 包装类型即可（如 `MapManager` 直接消费 `MapConfig`）。
 */
object ConfigQualifiers {
    /** [AttributeDefinition] Map：[CombatSessionFactory] 等需要 */
    val AttributeDefinitions = named("attributeDefinitions")

    /** [BuffDefinition] Map：[CombatSessionFactory] 创建敌人 BuffManager 时需要 */
    val BuffDefinitions = named("buffDefinitions")

    /** [EnemyDefinition] Map：[CombatSessionFactory] 实例化敌人时需要 */
    val EnemyDefinitions = named("enemyDefinitions")

    /** [QuestDefinition] Map：[QuestManager] 构造器需要 */
    val QuestDefinitions = named("questDefinitions")
}

/**
 * 配置加载 Koin 模块
 *
 * 把 10 个独立 `XxxConfigLoader.load()` 的产物登记为 Koin `single`，
 * 使 `data` / `adapter` / `game` 各层消费方可通过 `get()` 拿到。
 *
 * 共享解析工具（`EffectConfigLoader` / `RequiresParser`）是内部复用组件，
 * 不在此模块暴露为 Koin 单例。
 *
 * 覆盖默认加载（如测试用配置）的统一做法见 `项目组织规范.md` §7.3。
 *
 * @see CombatConfigResult 战斗配置的复合结果，在此模块中被拆分成
 *   [CombatConfig] 单例与 [ConfigQualifiers.EnemyDefinitions] 限定 Map
 */
val configModule = module {
    // 类型唯一的 data class 配置：直接注册
    single<AIConfig> { AIConfigLoader.load() }
    single<BuffConfig> { BuffConfigLoader.load() }
    single<CommandConfig> { CommandConfigLoader.load() }
    single<InventoryConfig> { InventoryConfigLoader.load() }
    single<MapConfig> { MapConfigLoader.load() }
    single<NpcConfig> { NpcConfigLoader.load() }
    single<QuestConfig> { QuestConfigLoader.load() }
    single<SkillConfig> { SkillConfigLoader.load() }

    // CombatConfigLoader 返回复合结构，拆成全局战斗配置 + 敌人定义 Map
    single<CombatConfigResult> { CombatConfigLoader.load() }
    single<CombatConfig> { get<CombatConfigResult>().combatConfig }
    single<Map<String, EnemyDefinition>>(ConfigQualifiers.EnemyDefinitions) {
        get<CombatConfigResult>().enemies
    }

    // AttributeConfigLoader 直接返回 Map，需独立 qualifier
    single<Map<String, AttributeDefinition>>(ConfigQualifiers.AttributeDefinitions) {
        AttributeConfigLoader.load()
    }

    // 下游真实消费的 Map 抽取：BuffDefinitions（CombatSessionFactory）+ QuestDefinitions（QuestManager）
    single<Map<String, BuffDefinition>>(ConfigQualifiers.BuffDefinitions) {
        get<BuffConfig>().buffs
    }
    single<Map<String, QuestDefinition>>(ConfigQualifiers.QuestDefinitions) {
        get<QuestConfig>().quests
    }
}

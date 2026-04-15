package org.textrpg.application.game

import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.textrpg.application.data.ConfigQualifiers
import org.textrpg.application.data.config.SkillConfig
import org.textrpg.application.domain.model.AttributeDefinition
import org.textrpg.application.domain.model.BuffDefinition
import org.textrpg.application.domain.model.CombatConfig
import org.textrpg.application.domain.model.EnemyDefinition
import org.textrpg.application.game.combat.CombatSessionFactory
import org.textrpg.application.game.combat.EnemyAI
import org.textrpg.application.game.command.CommandHandlerRegistry
import org.textrpg.application.game.command.CommandRouter
import org.textrpg.application.game.command.SessionManager
import org.textrpg.application.game.effect.EffectEngine
import org.textrpg.application.game.equipment.EquipmentService
import org.textrpg.application.game.inventory.InventoryService
import org.textrpg.application.game.map.MapManager
import org.textrpg.application.game.npc.NpcManager
import org.textrpg.application.game.quest.QuestManager
import org.textrpg.application.game.skill.CooldownManager
import org.textrpg.application.game.skill.SkillEngine
import org.textrpg.application.utils.script.KotlinScriptRunner

/**
 * 游戏核心 Koin 模块
 *
 * 注册 `game/` 下 13 个通用 Manager / Engine / Service 为全局单例，
 * 范例层只需 `get()` 即可获取，不再需要手动 new 与按顺序传参。
 *
 * **不包含的类及原因**：
 * - `BuffManager` / `AttributeContainer`：每实体一份，由范例层按需创建
 * - 范例层业务对象（如玩家管理、物品播种）：不属于框架
 * - `CommandHandler` 的具体实现：见范例层的 `handlerModule`
 *
 * **依赖**：`configModule`（配置）、`appModule`（[KotlinScriptRunner] / [CoroutineScope]）、
 * `repositoryModule`（[InventoryService] / [EquipmentService] 需要 `ItemRepository`）。
 *
 * 覆盖默认注册的方式见 `项目组织规范.md` §7.3。
 */
val gameModule = module {
    // ---- 特效 & 技能引擎 ----
    // EffectEngine 构造时已自动注册 12 个内置 atomic effect handler（见 EffectEngine.handlers 初始化）
    // 此处仅做依赖注入，不再重复 BuiltinEffectHandlers.createAll
    singleOf(::EffectEngine)

    // 全局 CooldownManager 默认实例（范例若需"每玩家一份"可覆盖）
    singleOf(::CooldownManager)

    // SkillEngine 构造后需 loadSkills(skillConfig) 初始化
    single<SkillEngine> {
        SkillEngine(effectEngine = get(), cooldownManager = get())
            .apply { loadSkills(get<SkillConfig>()) }
    }

    // ---- 地图 / NPC / 任务 ----
    singleOf(::MapManager)
    singleOf(::NpcManager)
    single<QuestManager> {
        QuestManager(questDefinitions = get(ConfigQualifiers.QuestDefinitions))
    }

    // ---- 背包 / 装备 ----
    singleOf(::InventoryService)
    singleOf(::EquipmentService)

    // ---- 指令路由 ----
    singleOf(::SessionManager)
    singleOf(::CommandHandlerRegistry)
    singleOf(::CommandRouter)

    // ---- 战斗 ----
    singleOf(::EnemyAI)
    single<CombatSessionFactory> {
        CombatSessionFactory(
            combatConfig = get<CombatConfig>(),
            enemyDefinitions = get<Map<String, EnemyDefinition>>(ConfigQualifiers.EnemyDefinitions),
            skillEngine = get(),
            effectEngine = get(),
            sessionManager = get(),
            enemyAI = get(),
            buffDefinitions = get<Map<String, BuffDefinition>>(ConfigQualifiers.BuffDefinitions),
            attributeDefinitions = get<Map<String, AttributeDefinition>>(ConfigQualifiers.AttributeDefinitions),
            coroutineScope = get<CoroutineScope>()
        )
    }
}

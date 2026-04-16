package org.textrpg.application.game.combat

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import org.textrpg.application.domain.model.AttributeDefinition
import org.textrpg.application.domain.model.BuffDefinition
import org.textrpg.application.domain.model.CombatConfig
import org.textrpg.application.domain.model.EffectResult
import org.textrpg.application.domain.model.EnemyDefinition
import org.textrpg.application.game.command.SessionManager
import org.textrpg.application.game.effect.AtomicEffectHandler
import org.textrpg.application.game.effect.EffectEngine
import org.textrpg.application.game.effect.EntityAccessor
import org.textrpg.application.game.skill.CooldownManager
import org.textrpg.application.game.skill.SkillEngine

/**
 * 战斗会话工厂
 *
 * 封装 [CombatSession] 的构造和所有依赖注入。
 * 提供 [createStartSessionHandler] 方法生成 [AtomicEffectHandler]，
 * 可注册到 [EffectEngine] 替换 `start_session` 的默认 stub。
 *
 * 使用示例：
 * ```kotlin
 * val factory = CombatSessionFactory(...)
 * effectEngine.registerHandler("start_session", factory.createStartSessionHandler())
 * ```
 *
 * @param combatConfig 全局战斗公式配置
 * @param enemyDefinitions 敌人定义映射
 * @param skillEngine 技能引擎
 * @param effectEngine 特效引擎
 * @param sessionManager 会话管理器
 * @param enemyAI AI 决策引擎
 * @param buffDefinitions Buff 定义映射（用于创建敌人的 BuffManager）
 * @param attributeDefinitions 属性定义映射（用于创建敌人的 AttributeContainer）
 * @param coroutineScope 协程作用域
 * @param logger 日志记录器（可选，默认创建独立实例）
 */
class CombatSessionFactory(
    private val combatConfig: CombatConfig,
    private val enemyDefinitions: Map<String, EnemyDefinition>,
    private val skillEngine: SkillEngine,
    private val effectEngine: EffectEngine,
    private val sessionManager: SessionManager,
    private val enemyAI: EnemyAI,
    private val buffDefinitions: Map<String, BuffDefinition>,
    private val attributeDefinitions: Map<String, AttributeDefinition>,
    private val coroutineScope: CoroutineScope,
    private val logger: KLogger
) {
    /**
     * 创建并启动一场战斗
     *
     * @param playerId 玩家 ID
     * @param enemyId 敌人定义 key
     * @param playerEntity 玩家的 EntityAccessor（用于获取属性等状态）
     * @param playerSkillIds 玩家可用技能列表
     * @param playerCooldownManager 玩家的冷却管理器
     * @param messageSink 消息发送回调（玩家侧）
     * @return 创建的战斗会话，敌人不存在时返回 null
     */
    fun createAndStart(
        playerId: Long,
        enemyId: String,
        playerEntity: EntityAccessor,
        playerSkillIds: List<String>,
        playerCooldownManager: CooldownManager,
        messageSink: suspend (String) -> Unit
    ): CombatSession? {
        val enemyDef = enemyDefinitions[enemyId]
        if (enemyDef == null) {
            logger.warn { "Enemy definition not found: $enemyId" }
            return null
        }

        // 创建敌人实体
        val enemy = CombatEntity.fromEnemyDefinition(
            def = enemyDef,
            logger = logger,
            attributeDefinitions = attributeDefinitions,
            buffDefinitions = buffDefinitions
        )

        // 包装玩家为 CombatEntity
        // 如果玩家 EntityAccessor 是 CombatEntity 或有公开的属性/Buff 引用，直接使用
        val (playerAttrContainer, playerBuffMgr) = resolvePlayerComponents(playerEntity)

        val player = CombatEntity(
            entityId = playerId.toString(),
            displayName = "你",
            isPlayer = true,
            attributeContainer = playerAttrContainer,
            buffManager = playerBuffMgr,
            skillIds = playerSkillIds,
            cooldownManager = playerCooldownManager,
            messageSink = messageSink,
            logger = logger
        )

        // 创建战斗会话
        val session = CombatSession(
            playerId = playerId,
            playerEntity = player,
            enemyEntity = enemy,
            enemyDefinition = enemyDef,
            combatConfig = combatConfig,
            skillEngine = skillEngine,
            effectEngine = effectEngine,
            enemyAI = enemyAI,
            sessionManager = sessionManager,
            coroutineScope = coroutineScope,
            logger = logger
        )

        // 注册会话并启动
        sessionManager.startSession(session)
        session.start()

        return session
    }

    /**
     * 创建 start_session 效果处理器
     *
     * 返回一个 [AtomicEffectHandler]，可注册到 [EffectEngine] 替换默认的 stub。
     * 效果参数：
     * - `sessionType` 必须为 "combat"
     * - `params["enemy_id"]` 指定敌人定义 key
     */
    fun createStartSessionHandler(): AtomicEffectHandler {
        return AtomicEffectHandler { effect, target, _ ->
            if (effect.sessionType != "combat") {
                return@AtomicEffectHandler EffectResult.failed(
                    "start_session: unsupported session type '${effect.sessionType}'"
                )
            }

            val enemyId = effect.params["enemy_id"]
                ?: return@AtomicEffectHandler EffectResult.failed("start_session: missing 'enemy_id' param")

            val session = createAndStart(
                playerId = target.entityId.toLongOrNull() ?: 0L,
                enemyId = enemyId,
                playerEntity = target,
                playerSkillIds = emptyList(), // 由调用方补充或从玩家数据加载
                playerCooldownManager = CooldownManager(),
                messageSink = { msg -> target.sendMessage(msg) }
            )

            if (session != null) {
                EffectResult.success("combat started: vs ${enemyDefinitions[enemyId]?.displayName ?: enemyId}")
            } else {
                EffectResult.failed("start_session: failed to create combat (enemy: $enemyId)")
            }
        }
    }

    // ======================== 玩家组件解析 ========================

    /**
     * 从玩家 EntityAccessor 解析 AttributeContainer 和 BuffManager
     *
     * 优先使用 CombatEntity 的公开 getter，否则使用 AttributeDefinitions 创建临时容器。
     */
    private fun resolvePlayerComponents(
        playerEntity: EntityAccessor
    ): Pair<org.textrpg.application.game.attribute.AttributeContainer, org.textrpg.application.game.buff.BuffManager> {
        // 如果已经是 CombatEntity，直接取
        if (playerEntity is CombatEntity) {
            return Pair(playerEntity.attributes, playerEntity.buffs)
        }

        // 创建新的容器（基于属性定义），并从 playerEntity 同步当前属性值
        // EntityAccessor.getAttributeValue 约定不抛异常（未知 key 返回 0.0），无需 try-catch
        val attrContainer = org.textrpg.application.game.attribute.AttributeContainer(attributeDefinitions)
        for (key in attrContainer.getAllKeys()) {
            attrContainer.setBaseValue(key, playerEntity.getAttributeValue(key))
        }

        val buffMgr = org.textrpg.application.game.buff.BuffManager(buffDefinitions, attrContainer, null, logger)
        return Pair(attrContainer, buffMgr)
    }
}

package org.textrpg.application.game.combat

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.textrpg.application.domain.model.EnemyDefinition
import org.textrpg.application.game.attribute.FormulaEngine
import org.textrpg.application.utils.script.KotlinScriptRunner
import java.io.File

/**
 * AI 行动类型
 */
enum class AIActionType {
    /** 使用技能 */
    SKILL,
    /** 防御 */
    DEFEND,
    /** 逃跑 */
    FLEE
}

/**
 * AI 决策结果
 *
 * @property actionType 行动类型
 * @property skillId 技能 ID（仅 [AIActionType.SKILL] 时有效）
 */
data class AIDecision(
    val actionType: AIActionType,
    val skillId: String? = null
)

/**
 * 战斗状态快照
 *
 * 传递给 AI 决策引擎的当前战斗状态。
 *
 * @property roundNumber 当前回合数
 * @property playerEntity 玩家参战实体
 * @property enemyEntity 敌人参战实体
 */
data class CombatState(
    val roundNumber: Int,
    val playerEntity: CombatEntity,
    val enemyEntity: CombatEntity
)

/**
 * 敌人 AI 决策引擎
 *
 * 支持两种决策模式：
 * 1. **规则配置**：按 [AIRule.priority] 降序遍历，首个条件满足（公式值 > 0）的规则触发
 * 2. **脚本覆盖**：设置 [EnemyDefinition.aiScript] 时完全由脚本接管决策
 *
 * 条件公式中 `self.xxx` 引用敌人属性，`target.xxx` 引用玩家属性。
 * 特殊值 `"true"` 和 `"1"` 始终满足，可作为默认行为的条件。
 *
 * @param scriptRunner Kotlin 脚本执行器（可选，脚本模式需要）
 * @param logger 日志记录器（可选，默认创建独立实例）
 */
class EnemyAI(
    private val scriptRunner: KotlinScriptRunner? = null,
    private val logger: KLogger? = null
) {
    private val log = logger ?: KotlinLogging.logger {}

    /**
     * 做出 AI 决策
     *
     * @param enemy 敌人参战实体
     * @param definition 敌人定义（含 AI 规则或脚本路径）
     * @param state 当前战斗状态
     * @return AI 决策结果
     */
    suspend fun decide(
        enemy: CombatEntity,
        definition: EnemyDefinition,
        state: CombatState
    ): AIDecision {
        // 脚本模式优先
        if (definition.aiScript != null && scriptRunner != null) {
            return decideByScript(enemy, definition, state)
        }

        // 规则模式
        return decideByRules(enemy, definition, state)
    }

    /**
     * 规则模式决策
     */
    private fun decideByRules(
        enemy: CombatEntity,
        definition: EnemyDefinition,
        state: CombatState
    ): AIDecision {
        // 按 priority 降序排列
        val sortedRules = definition.aiRules.sortedByDescending { it.priority }

        for (rule in sortedRules) {
            if (evaluateCondition(rule.condition, enemy, state)) {
                return parseAction(rule.action, definition)
            }
        }

        // 兜底：使用第一个技能，或防御
        return if (definition.skills.isNotEmpty()) {
            AIDecision(AIActionType.SKILL, definition.skills.first())
        } else {
            AIDecision(AIActionType.DEFEND)
        }
    }

    /**
     * 脚本模式决策
     */
    private fun decideByScript(
        enemy: CombatEntity,
        definition: EnemyDefinition,
        state: CombatState
    ): AIDecision {
        val scriptPath = definition.aiScript ?: return AIDecision(AIActionType.DEFEND)
        val file = File(scriptPath)
        if (!file.exists()) {
            log.warn { "AI script not found: $scriptPath, falling back to rules" }
            return decideByRules(enemy, definition, state)
        }

        val context = mapOf<String, Any?>(
            "enemy" to enemy,
            "player" to state.playerEntity,
            "roundNumber" to state.roundNumber,
            "skills" to definition.skills
        )

        val result = scriptRunner!!.executeScript(file.readText(), context)
        if (result.success && result.message.isNotBlank()) {
            return parseAction(result.message.trim(), definition)
        }

        // 脚本失败，回退到规则
        log.warn { "AI script failed: ${result.message}, falling back to rules" }
        return decideByRules(enemy, definition, state)
    }

    /**
     * 求值 AI 条件公式
     *
     * `self.xxx` 引用敌人属性，`target.xxx` 引用玩家属性。
     * "true" 或 "1" 始终返回 true。
     *
     * @return 条件是否满足（公式值 > 0）
     */
    private fun evaluateCondition(
        condition: String,
        enemy: CombatEntity,
        state: CombatState
    ): Boolean {
        // 快捷判定
        if (condition == "true" || condition == "1") return true
        if (condition == "false" || condition == "0") return false

        return runCatching {
            // 预处理点前缀
            val processed = condition
                .replace("self.", "self_")
                .replace("target.", "target_")

            FormulaEngine.evaluate(processed) { key ->
                when {
                    key.startsWith("self_") -> enemy.getAttributeValue(key.removePrefix("self_"))
                    key.startsWith("target_") -> state.playerEntity.getAttributeValue(key.removePrefix("target_"))
                    else -> enemy.getAttributeValue(key)
                }
            } > 0
        }.getOrElse { e ->
            log.warn(e) { "AI condition evaluation failed: '$condition'" }
            false
        }
    }

    /**
     * 解析动作字符串为 AI 决策
     *
     * 优先识别特殊关键字（`defend` / `flee`，大小写不敏感），
     * 否则按技能 ID 处理（保持原样大小写匹配）。技能 ID 必须在
     * [EnemyDefinition.skills] 白名单中——不在白名单的字符串视为配置错误，
     * 回退为 [AIActionType.DEFEND] 防止把非法技能传给 [SkillEngine] 导致回合空转。
     *
     * @param action 来自 [AIRule.action] 或 AI 脚本的动作字符串
     * @param definition 敌人定义（提供技能白名单）
     * @return AI 决策
     */
    private fun parseAction(action: String, definition: EnemyDefinition): AIDecision {
        when (action.lowercase()) {
            "defend" -> return AIDecision(AIActionType.DEFEND)
            "flee" -> return AIDecision(AIActionType.FLEE)
        }
        if (action in definition.skills) {
            return AIDecision(AIActionType.SKILL, action)
        }
        log.warn { "AI action '$action' is not a known keyword nor in skills ${definition.skills}, falling back to DEFEND" }
        return AIDecision(AIActionType.DEFEND)
    }
}

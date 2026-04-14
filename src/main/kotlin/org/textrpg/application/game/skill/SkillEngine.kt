package org.textrpg.application.game.skill

import org.textrpg.application.data.config.SkillConfig
import org.textrpg.application.domain.model.EffectResult
import org.textrpg.application.domain.model.SkillDefinition
import org.textrpg.application.game.command.RequiresChecker
import org.textrpg.application.game.effect.EffectContext
import org.textrpg.application.game.effect.EffectEngine

/**
 * 技能执行结果
 *
 * @property success 技能是否成功执行
 * @property message 失败原因或执行反馈
 * @property effectResults 各效果的执行结果列表（仅成功执行时有内容）
 */
data class SkillResult(
    val success: Boolean,
    val message: String? = null,
    val effectResults: List<EffectResult> = emptyList()
) {
    companion object {
        fun failed(message: String) = SkillResult(success = false, message = message)
    }
}

/**
 * 技能引擎
 *
 * 在特效引擎之上封装完整的技能使用管道：
 *
 * ```
 * 查找技能 → Requires 校验 → Cooldown 检查 → Cost 校验+扣除 → Effects 执行 → 记录 Cooldown
 * ```
 *
 * 复用指令系统的 [RequiresChecker] 进行前置条件校验（通过 [SkillContextAdapter] 桥接），
 * 复用特效引擎的 [EffectEngine] 执行效果列表。
 *
 * 使用示例：
 * ```kotlin
 * val skillEngine = SkillEngine(effectEngine, cooldownManager)
 * skillEngine.loadSkills(SkillConfigLoader.load())
 *
 * val context = SimpleEffectContext(source = playerAccessor, primaryTarget = enemyAccessor)
 * val result = skillEngine.useSkill("fireball", context)
 * if (!result.success) {
 *     // 提示玩家失败原因
 * }
 * ```
 *
 * @param effectEngine 特效引擎实例
 * @param cooldownManager 冷却管理器实例
 */
class SkillEngine(
    private val effectEngine: EffectEngine,
    private val cooldownManager: CooldownManager
) {

    /** 技能定义注册表（skill_key → SkillDefinition） */
    private var skills: Map<String, SkillDefinition> = emptyMap()

    /**
     * 加载技能配置
     *
     * @param config 从 YAML 加载的技能配置
     */
    fun loadSkills(config: SkillConfig) {
        skills = config.skills
    }

    /**
     * 获取技能定义
     *
     * @param skillId 技能标识符
     * @return 技能定义，不存在时返回 null
     */
    fun getSkill(skillId: String): SkillDefinition? = skills[skillId]

    /**
     * 获取所有技能定义
     *
     * @return 技能标识符 → 技能定义 的映射
     */
    fun getAllSkills(): Map<String, SkillDefinition> = skills

    /**
     * 使用技能
     *
     * 执行完整的技能使用管道。任何步骤失败都会立即返回失败结果，不执行后续步骤。
     * Cost 扣除采用原子策略：先校验所有消耗是否满足，再统一扣除。
     *
     * @param skillId 技能标识符
     * @param context 特效执行上下文（包含施法者和目标信息）
     * @param sessionType 当前会话类型（传递给 RequiresChecker）
     * @param nodeTags 当前地图节点标签（传递给 RequiresChecker）
     * @return 技能执行结果
     */
    suspend fun useSkill(
        skillId: String,
        context: EffectContext,
        sessionType: String? = null,
        nodeTags: Set<String> = emptySet()
    ): SkillResult {
        // 1. 查找技能定义
        val skill = skills[skillId]
            ?: return SkillResult.failed("未知技能：$skillId")

        // 2. Requires 校验
        val requiresAdapter = SkillContextAdapter(
            entity = context.source,
            sessionType = sessionType,
            nodeTags = nodeTags
        )
        val requireResult = RequiresChecker.check(skill.requires, requiresAdapter)
        if (!requireResult.passed) {
            return SkillResult.failed(requireResult.message ?: "前置条件不满足")
        }

        // 3. Cooldown 检查
        val entityId = context.source.entityId
        if (cooldownManager.isOnCooldown(entityId, skillId)) {
            val remaining = cooldownManager.getCooldown(entityId, skillId)
            return SkillResult.failed("${skill.displayName} 冷却中，剩余 $remaining 回合")
        }

        // 4. Cost 校验（先检查所有消耗是否满足）
        for ((attrKey, amount) in skill.cost) {
            val current = context.source.getAttributeValue(attrKey)
            if (current < amount) {
                return SkillResult.failed("${attrKey} 不足（需要 $amount，当前 $current）")
            }
        }

        // 5. Cost 扣除（all-or-nothing）
        for ((attrKey, amount) in skill.cost) {
            context.source.modifyAttribute(attrKey, -amount)
        }

        // 6. 执行 Effects + customScript
        val effectResults = effectEngine.executeWithScript(
            effects = skill.effects,
            context = context,
            customScript = skill.customScript
        )

        // 7. 记录 Cooldown
        if (skill.cooldown > 0) {
            cooldownManager.recordCooldown(entityId, skillId, skill.cooldown)
        }

        return SkillResult(
            success = true,
            message = "${skill.displayName} 释放成功",
            effectResults = effectResults
        )
    }
}

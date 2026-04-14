package org.textrpg.application.game.effect

import org.textrpg.application.game.attribute.FormulaEngine

/**
 * 特效执行上下文接口
 *
 * 为特效引擎提供施法者、目标、公式求值等运行时信息。
 * 不同的游戏场景（非战斗 / 战斗 / 脚本触发）提供不同的上下文实现。
 *
 * 设计说明：
 * - [source] 是效果的发起者（施法者、使用物品的玩家等）
 * - [primaryTarget] 是显式指定的目标（技能选择的对象），非目标型效果为 null
 * - [resolveTargets] 将目标选择器字符串解析为实际的实体列表
 * - [resolveFormula] 在指定实体的属性上下文中求值公式
 *
 * @see SimpleEffectContext
 */
interface EffectContext {

    /** 效果发起者（施法者 / 使用者） */
    val source: EntityAccessor

    /** 显式指定的目标（可为 null，如自我施法或无目标效果） */
    val primaryTarget: EntityAccessor?

    /**
     * 根据目标选择器解析目标实体列表
     *
     * 内置选择器参见 [TargetSelector]。战斗上下文可扩展实现群体选择器。
     *
     * @param selector 目标选择器字符串（如 "self"、"target"、"all_enemies"）
     * @return 目标实体列表，选择器无法解析时返回空列表
     */
    fun resolveTargets(selector: String): List<EntityAccessor>

    /**
     * 在指定实体的属性上下文中求值公式
     *
     * 公式中的属性引用（如 "strength"、"physical_attack"）会从实体的属性中解析。
     * 委托给 [FormulaEngine] 进行表达式解析和求值。
     *
     * @param formula 公式字符串
     * @param entity 提供属性值的实体
     * @return 计算结果
     */
    fun resolveFormula(formula: String, entity: EntityAccessor): Double
}

/**
 * 简易特效上下文
 *
 * 适用于非战斗场景的基础实现。
 * - `self` 解析为 [source]
 * - `target` 解析为 [primaryTarget]（存在时）
 * - 群体选择器（all_enemies 等）返回空列表，待战斗系统实现后由 CombatEffectContext 扩展
 *
 * @param source 效果发起者
 * @param primaryTarget 显式目标（可选）
 */
open class SimpleEffectContext(
    override val source: EntityAccessor,
    override val primaryTarget: EntityAccessor? = null
) : EffectContext {

    override fun resolveTargets(selector: String): List<EntityAccessor> {
        return when (selector) {
            "self" -> listOf(source)
            "target" -> listOfNotNull(primaryTarget)
            "all_enemies", "all_allies", "random_enemy", "random_ally", "all" -> {
                // 群体选择器在非战斗上下文中无意义，返回空列表
                // 战斗系统（Step 5）会提供 CombatEffectContext 扩展这些选择器
                emptyList()
            }
            else -> {
                println("Warning: Unknown target selector '$selector', returning empty list")
                emptyList()
            }
        }
    }

    override fun resolveFormula(formula: String, entity: EntityAccessor): Double {
        return FormulaEngine.evaluate(formula) { key -> entity.getAttributeValue(key) }
    }
}

package org.textrpg.application.game.buff

import org.textrpg.application.game.attribute.FormulaEngine
import org.textrpg.application.game.effect.EffectContext
import org.textrpg.application.game.effect.EntityAccessor

/**
 * Buff 周期效果执行上下文
 *
 * 包装一个已有的 [EffectContext]，在公式求值时注入 Buff 特有的变量（如 `stacks`）。
 * 使用 Kotlin 接口委托（`by delegate`），仅 override [resolveFormula]，其余方法原样透传。
 *
 * 这样 tick_effects 中的公式（如 `"-10 * stacks"`）就可以正确引用当前 Buff 的层数，
 * 而不需要修改 [FormulaEngine] 或 [EffectContext] 接口。
 *
 * @param delegate 被包装的原始上下文
 * @param buffInstance 当前正在 tick 的 Buff 实例（提供 stacks 等变量）
 */
class BuffTickEffectContext(
    private val delegate: EffectContext,
    private val buffInstance: BuffInstance
) : EffectContext by delegate {

    /**
     * 在公式求值时注入 Buff 变量
     *
     * 当前注入的变量：
     * - `stacks`：当前 Buff 层数
     *
     * 未匹配的标识符回退到实体属性解析。
     */
    override fun resolveFormula(formula: String, entity: EntityAccessor): Double {
        return FormulaEngine.evaluate(formula) { key ->
            when (key) {
                "stacks" -> buffInstance.stacks.toDouble()
                else -> entity.getAttributeValue(key)
            }
        }
    }
}

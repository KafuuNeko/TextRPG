package org.textrpg.application.game.attribute

import org.textrpg.application.domain.model.AttributeDefinition
import org.textrpg.application.domain.model.Modifier
import org.textrpg.application.domain.model.ModifierType

/**
 * 属性运行时实例
 *
 * 管理单个属性的基础值、修正器栈和最终值计算。
 * 采用脏标记（dirty flag）机制：修正器变动时标记为脏，
 * 下次读取最终值时才重新计算，避免频繁无效运算。
 *
 * 修正器计算流程（默认）：
 * 1. 以 [baseValue] 为起点
 * 2. 累加所有 [ModifierType.FLAT] 修正器
 * 3. 乘算所有 [ModifierType.PERCENT] 修正器（加算合并后一次乘算）
 * 4. 裁剪至属性定义的 [min, max] 范围
 *
 * @property definition 属性定义（不可变模板，来自 YAML 配置）
 */
class AttributeInstance(val definition: AttributeDefinition) {

    /** 基础值，一级属性通过直接赋值修改，二级属性由公式引擎更新 */
    private var baseValue: Double = definition.defaultValue

    /** 修正器栈，按添加顺序存储 */
    private val modifiers: MutableList<Modifier> = mutableListOf()

    /** 缓存的最终计算值 */
    private var cachedFinalValue: Double = baseValue

    /** 脏标记：为 true 时下次 getFinalValue 会重新计算 */
    private var dirty: Boolean = true

    /**
     * 获取基础值（不含修正器）
     */
    fun getBaseValue(): Double = baseValue

    /**
     * 设置基础值
     *
     * 不自动裁剪——裁剪在 [getFinalValue] 时统一执行，
     * 因为修正器可能将值拉回合法范围。
     *
     * @param value 新的基础值
     */
    fun setBaseValue(value: Double) {
        if (baseValue != value) {
            baseValue = value
            dirty = true
        }
    }

    /**
     * 获取最终值（基础值 + 修正器计算 + 范围裁剪）
     *
     * 使用脏标记缓存：仅在基础值或修正器变化后重新计算。
     * **注意**：此值不包含跨属性约束（如 boundMax），
     * 跨属性约束由 [AttributeContainer] 在返回值时统一处理。
     */
    fun getFinalValue(): Double {
        if (dirty) recalculate()
        return cachedFinalValue
    }

    // ======================== 修正器操作 ========================

    /**
     * 添加修正器
     *
     * @param modifier 要添加的修正器实例
     */
    fun addModifier(modifier: Modifier) {
        modifiers.add(modifier)
        dirty = true
    }

    /**
     * 移除指定修正器实例
     *
     * @param modifier 要移除的修正器（引用相等）
     * @return 是否成功移除
     */
    fun removeModifier(modifier: Modifier): Boolean {
        val removed = modifiers.remove(modifier)
        if (removed) dirty = true
        return removed
    }

    /**
     * 按来源批量移除修正器
     *
     * 适用于装备脱下、Buff 消失等场景，通过 source 标识一次性移除
     * 该来源添加的所有修正器。
     *
     * @param source 来源标识（如 "equipment:slot_weapon"、"buff:burn_dot"）
     * @return 被移除的修正器数量
     */
    fun removeModifiersBySource(source: String): Int {
        val sizeBefore = modifiers.size
        modifiers.removeAll { it.source == source }
        val removed = sizeBefore - modifiers.size
        if (removed > 0) dirty = true
        return removed
    }

    /**
     * 检查是否存在指定来源的修正器
     *
     * @param source 来源标识
     */
    fun hasModifierFromSource(source: String): Boolean =
        modifiers.any { it.source == source }

    /**
     * 获取当前所有修正器的只读副本
     */
    fun getModifiers(): List<Modifier> = modifiers.toList()

    /**
     * 清空所有修正器
     */
    fun clearModifiers() {
        if (modifiers.isNotEmpty()) {
            modifiers.clear()
            dirty = true
        }
    }

    // ======================== 内部计算 ========================

    /**
     * 重新计算最终值
     *
     * 计算流程：
     * 1. 累加所有 FLAT 修正器到基础值上
     * 2. 将所有 PERCENT 修正器加算合并，一次乘算（两个 +10% = +20%，非连乘）
     * 3. 裁剪至属性定义的 [min, max]
     */
    private fun recalculate() {
        val flatSum = modifiers
            .filter { it.type == ModifierType.FLAT }
            .sumOf { it.value }

        val percentSum = modifiers
            .filter { it.type == ModifierType.PERCENT }
            .sumOf { it.value }

        var result = baseValue + flatSum
        if (percentSum != 0.0) {
            result *= (1.0 + percentSum / 100.0)
        }

        cachedFinalValue = result.coerceIn(definition.min, definition.max)
        dirty = false
    }
}

package org.textrpg.application.domain.model

/**
 * Buff 叠加策略枚举
 *
 * 决定同一 Buff 重复施加时的行为。引擎内置三种策略，脚本可通过自定义逻辑扩展。
 */
enum class StackPolicy(val value: String) {
    /** 刷新：重复施加时刷新持续时间（取较长者），不增加层数 */
    REFRESH("refresh"),
    /** 叠加：层数递增（直到 maxStacks），每层增强修正器和周期效果 */
    STACK("stack"),
    /** 独立：每次施加独立存在，各自计时，允许多个同名 Buff 共存 */
    INDEPENDENT("independent");

    companion object {
        fun fromValue(value: String): StackPolicy =
            entries.find { it.value == value } ?: REFRESH
    }
}

/**
 * Buff 修正器定义（不可变领域模型）
 *
 * Buff 持续期间挂载在目标属性上的修正器模板。Buff 移除时对应修正器自动回滚。
 * STACK 策略下，修正器数值按层数缩放（value * stacks）。
 *
 * @property attribute 目标属性 key
 * @property type 修正类型，参见 [ModifierType]
 * @property value 单层修正值（STACK 策略下实际值 = value * stacks）
 * @property priority 计算优先级（默认 0）
 */
data class BuffModifierDefinition(
    val attribute: String,
    val type: ModifierType,
    val value: Double,
    val priority: Int = 0
)

/**
 * Buff 定义（不可变领域模型）
 *
 * 从 YAML 配置加载的 Buff 模板。Buff 本质上是"挂在实体身上的、
 * 会随回合触发的 Effect 集合 + 持续性属性修正器"。
 *
 * Buff 的生命周期：
 * ```
 * 施加 → 检查叠加策略 → 挂载 modifiers → 触发 on_apply
 * 每回合结束 → 执行 tick_effects → duration -1 → 过期则移除
 * 移除 → 回滚 modifiers → 触发 on_remove
 * ```
 *
 * @property key Buff 唯一标识符
 * @property displayName 显示名称
 * @property description 说明文本
 * @property maxStacks 最大叠加层数（STACK / INDEPENDENT 策略有效）
 * @property duration 持续回合数（-1 表示永久）
 * @property stackPolicy 叠加策略
 * @property modifiers 持续期间生效的属性修正器列表
 * @property tickEffects 每回合触发的周期效果列表
 * @property onApply 施加时执行的脚本路径（可选）
 * @property onRemove 移除时执行的脚本路径（可选）
 * @property onStack 叠加时执行的脚本路径（可选，仅 STACK 策略有效）
 */
data class BuffDefinition(
    val key: String,
    val displayName: String = "",
    val description: String = "",
    val maxStacks: Int = 1,
    val duration: Int = -1,
    val stackPolicy: StackPolicy = StackPolicy.REFRESH,
    val modifiers: List<BuffModifierDefinition> = emptyList(),
    val tickEffects: List<EffectDefinition> = emptyList(),
    val onApply: String? = null,
    val onRemove: String? = null,
    val onStack: String? = null
)

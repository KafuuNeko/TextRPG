package org.textrpg.application.domain.model

/**
 * 属性定义（不可变领域模型）
 *
 * 从 YAML 配置加载的属性模板，定义属性的元信息、取值范围、计算公式和触发器。
 * 引擎不预设任何具体游戏概念（如"HP"、"力量"），所有语义通过配置赋予。
 *
 * 一级属性（[AttributeTier.PRIMARY]）为纯存储值，通过直接赋值或修正器修改。
 * 二级属性（[AttributeTier.DERIVED]）由 [formula] 从其他属性计算而来，支持嵌套引用。
 *
 * @property key 属性唯一标识符（如 "strength"、"max_hp"），对应 YAML 中的 key
 * @property tier 属性层级：一级（纯存储）或二级（公式计算）
 * @property displayName 显示名称，用于玩家界面展示
 * @property description 属性说明文本
 * @property defaultValue 初始默认值
 * @property min 最小值限制
 * @property max 最大值限制
 * @property formula 计算公式（仅二级属性有效），支持四则运算和属性引用（如 "strength * 2 + 50"）
 * @property boundMax 当前值上限绑定的属性 key（如 current_hp 绑定 max_hp），值不会超过该属性的当前值
 * @property triggers 触发器映射，key 为触发器类型，value 为触发行为定义
 * @property regen 自动回复配置（可选），适用于行动力等资源型属性
 */
data class AttributeDefinition(
    val key: String,
    val tier: AttributeTier = AttributeTier.PRIMARY,
    val displayName: String = "",
    val description: String = "",
    val defaultValue: Double = 0.0,
    val min: Double = 0.0,
    val max: Double = 9999.0,
    val formula: String? = null,
    val boundMax: String? = null,
    val triggers: Map<TriggerType, TriggerDefinition> = emptyMap(),
    val regen: RegenDefinition? = null
)

/**
 * 触发器定义
 *
 * 配置属性值变化时的触发行为。[action] 支持两种格式：
 * - **内置行为**：直接写行为名称，如 `"death"`、`"block_commands"`
 * - **脚本扩展**：以 `"script:"` 前缀指定脚本路径，如 `"script:low_hp_warning.kts"`
 *
 * @property action 触发时执行的行为标识
 * @property value 阈值表达式（仅 [TriggerType.ON_THRESHOLD] 使用），支持公式引用其他属性
 */
data class TriggerDefinition(
    val action: String,
    val value: String? = null
)

/**
 * 自动回复配置
 *
 * 定义属性值的自动恢复策略，适用于行动力、体力等需要随时间回复的资源型属性。
 *
 * @property amount 每次回复的数值
 * @property interval 回复间隔，支持时间单位后缀：s（秒）、m（分钟）、h（小时）
 */
data class RegenDefinition(
    val amount: Double,
    val interval: String
)

/**
 * 属性修正器
 *
 * 挂载在属性实例上的数值修正。装备穿戴、Buff 施加等操作通过添加修正器来影响属性最终值，
 * 移除修正器时属性自动回滚。同一属性可同时拥有多个修正器，按 [priority] 排序后依次计算。
 *
 * 默认计算顺序：基础值 + SUM(FLAT 修正器)，然后依次乘算 PERCENT 修正器。
 *
 * @property source 来源标识，用于按来源批量移除（如 "equipment:slot_weapon"、"buff:burn_dot"）
 * @property type 修正类型：加算（[ModifierType.FLAT]）或乘算（[ModifierType.PERCENT]）
 * @property value 修正值。FLAT 为绝对值增减，PERCENT 为百分比（如 10.0 表示 +10%）
 * @property priority 计算优先级，值越小越先计算（默认为 0）
 */
data class Modifier(
    val source: String,
    val type: ModifierType,
    val value: Double,
    val priority: Int = 0
)

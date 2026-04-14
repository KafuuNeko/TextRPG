package org.textrpg.application.domain.model

/**
 * 技能定义（不可变领域模型）
 *
 * 从 YAML 配置加载的技能模板。技能是框架的"万物皆技能"抽象——
 * 普通攻击、魔法、使用消耗品都统一为技能定义，底层走相同的执行管道：
 *
 * ```
 * Requires 校验 → Cooldown 检查 → Cost 扣除 → Effects 执行 → 记录 Cooldown
 * ```
 *
 * 复用指令系统的 [RequiresDefinition] 作为前置条件，复用特效引擎执行效果列表。
 *
 * @property key 技能唯一标识符（对应 YAML 中的 key）
 * @property displayName 技能显示名称
 * @property description 技能说明文本
 * @property requires 前置条件定义（与指令系统共用 [RequiresDefinition]）
 * @property cost 消耗映射（属性 key → 消耗量），执行前原子校验并扣除
 * @property cooldown 冷却回合数（0 表示无冷却）
 * @property effects 效果列表，按顺序执行的 [EffectDefinition] 序列
 * @property customScript 可选的后置脚本路径，在所有效果执行完毕后运行
 */
data class SkillDefinition(
    val key: String,
    val displayName: String = "",
    val description: String = "",
    val requires: RequiresDefinition = RequiresDefinition(),
    val cost: Map<String, Double> = emptyMap(),
    val cooldown: Int = 0,
    val effects: List<EffectDefinition> = emptyList(),
    val customScript: String? = null
)

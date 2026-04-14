package org.textrpg.application.domain.model

/**
 * 战斗全局配置
 *
 * 定义战斗系统的通用公式和参数。公式中可使用前缀引用：
 * - `attacker.xxx`：攻击者的属性
 * - `defender.xxx`：防御者的属性
 * - `random(N)`：0 到 N-1 的随机数
 *
 * @property damageFormula 默认伤害公式（可被脚本覆盖）
 * @property critCheck 暴击判定公式（结果 > 0 表示暴击）
 * @property critMultiplier 暴击伤害倍率
 * @property minDamage 最低伤害值
 * @property fleeCheck 逃跑判定公式（结果 > 0 表示成功，null 表示不允许逃跑）
 * @property defaultTimeoutSeconds 玩家输入超时秒数
 */
data class CombatConfig(
    val damageFormula: String = "attacker.physical_attack - defender.defense * 0.5",
    val critCheck: String = "random(100) - attacker.crit_rate",
    val critMultiplier: Double = 1.5,
    val minDamage: Double = 1.0,
    val fleeCheck: String? = null,
    val defaultTimeoutSeconds: Long = 60
)

/**
 * 敌人定义（不可变领域模型）
 *
 * 从 YAML 配置加载的敌人模板。包含属性初始值、可用技能、AI 规则和奖励。
 *
 * @property key 敌人唯一标识符
 * @property displayName 显示名称
 * @property attributes 初始属性映射（attribute_key → value）
 * @property skills 可用技能 ID 列表
 * @property aiRules AI 规则列表（按 priority 降序执行，首个满足条件的触发）
 * @property aiScript AI 脚本路径（设置时完全覆盖规则配置）
 * @property rewards 战斗奖励配置
 */
data class EnemyDefinition(
    val key: String,
    val displayName: String = "",
    val attributes: Map<String, Double> = emptyMap(),
    val skills: List<String> = emptyList(),
    val aiRules: List<AIRule> = emptyList(),
    val aiScript: String? = null,
    val rewards: CombatRewards? = null
)

/**
 * AI 规则
 *
 * 敌人 AI 的单条决策规则。引擎按 [priority] 降序遍历，
 * 用 FormulaEngine 求值 [condition]，结果 > 0 表示条件满足，执行对应 [action]。
 *
 * 条件公式中 `self.xxx` 引用敌人自身属性，`target.xxx` 引用玩家属性。
 *
 * @property condition 条件公式（"true" 等价于 "1"，始终满足，可作为默认行为）
 * @property action 技能 ID 或特殊动作（"defend"、"flee"）
 * @property priority 优先级（越高越先检查）
 */
data class AIRule(
    val condition: String,
    val action: String,
    val priority: Int = 0
)

/**
 * 战斗奖励配置
 *
 * @property expFormula 经验值公式（引用 `enemy.xxx` 或 `player.xxx`），null 表示无经验
 * @property drops 掉落物列表
 */
data class CombatRewards(
    val expFormula: String? = null,
    val drops: List<DropDefinition> = emptyList()
)

/**
 * 掉落物定义
 *
 * @property item 物品模板 ID（字符串，由调用方转换）
 * @property chance 掉落概率（0.0 ~ 1.0）
 * @property quantityMin 最小数量
 * @property quantityMax 最大数量
 */
data class DropDefinition(
    val item: String,
    val chance: Double = 1.0,
    val quantityMin: Int = 1,
    val quantityMax: Int = 1
)

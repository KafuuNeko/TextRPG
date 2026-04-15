package org.textrpg.application.data.config

import org.textrpg.application.domain.model.*

/**
 * 战斗配置加载结果
 *
 * @property combatConfig 全局战斗公式配置
 * @property enemies 敌人定义映射（enemy_key → [EnemyDefinition]）
 */
data class CombatConfigResult(
    val combatConfig: CombatConfig = CombatConfig(),
    val enemies: Map<String, EnemyDefinition> = emptyMap()
)

/**
 * 战斗配置加载器
 *
 * 从 YAML 文件加载全局战斗公式和敌人定义。
 *
 * YAML 格式示例：
 * ```yaml
 * combat:
 *   damageFormula: "attacker.physical_attack - defender.defense * 0.5"
 *   critMultiplier: 1.5
 *   minDamage: 1
 *   defaultTimeoutSeconds: 60
 *
 * enemies:
 *   goblin:
 *     displayName: "哥布林"
 *     attributes:
 *       current_hp: 50
 *       max_hp: 50
 *     skills: ["slash"]
 *     aiRules:
 *       - condition: "1"
 *         action: "slash"
 *         priority: 1
 *     rewards:
 *       expFormula: "enemy.max_hp * 0.5"
 *       drops:
 *         - item: "goblin_ear"
 *           chance: 0.5
 *           quantity: "1-3"
 * ```
 */
object CombatConfigLoader : AbstractYamlLoader<CombatConfigResult>() {
    override val defaultPath = "src/main/resources/config/combat.yaml"
    override val default = CombatConfigResult()
    override val configName = "Combat"

    override fun parse(raw: Map<String, Any>): CombatConfigResult {
        @Suppress("UNCHECKED_CAST")
        val combatConfig = parseCombatConfig(raw["combat"] as? Map<String, Any>)
        @Suppress("UNCHECKED_CAST")
        val enemiesRaw = raw["enemies"] as? Map<String, Map<String, Any>> ?: emptyMap()
        val enemies = enemiesRaw.mapValues { (key, props) -> parseEnemy(key, props) }
        return CombatConfigResult(combatConfig = combatConfig, enemies = enemies)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseCombatConfig(raw: Map<String, Any>?): CombatConfig {
        if (raw == null) return CombatConfig()
        return CombatConfig(
            damageFormula = raw["damageFormula"] as? String
                ?: "attacker.physical_attack - defender.defense * 0.5",
            critCheck = raw["critCheck"] as? String
                ?: "random(100) - attacker.crit_rate",
            critMultiplier = RequiresParser.toDouble(raw["critMultiplier"]) ?: 1.5,
            minDamage = RequiresParser.toDouble(raw["minDamage"]) ?: 1.0,
            fleeCheck = raw["fleeCheck"] as? String,
            defaultTimeoutSeconds = (raw["defaultTimeoutSeconds"] as? Number)?.toLong() ?: 60,
            attributeKeys = parseAttributeKeys(raw["attributeKeys"] as? Map<String, Any>)
        )
    }

    private fun parseAttributeKeys(raw: Map<String, Any>?): CombatAttributeKeys {
        if (raw == null) return CombatAttributeKeys()
        val defaults = CombatAttributeKeys()
        return CombatAttributeKeys(
            currentHp = raw["currentHp"] as? String ?: defaults.currentHp,
            maxHp = raw["maxHp"] as? String ?: defaults.maxHp,
            exp = raw["exp"] as? String ?: defaults.exp
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseEnemy(key: String, props: Map<String, Any>): EnemyDefinition {
        return EnemyDefinition(
            key = key,
            displayName = props["displayName"] as? String ?: key,
            attributes = (props["attributes"] as? Map<String, Any>)
                ?.mapValues { (_, v) -> RequiresParser.toDouble(v) ?: 0.0 }
                ?: emptyMap(),
            skills = (props["skills"] as? List<*>)?.map { it.toString() } ?: emptyList(),
            aiRules = parseAIRules(props["aiRules"]),
            aiScript = props["aiScript"] as? String,
            rewards = parseRewards(props["rewards"])
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseAIRules(raw: Any?): List<AIRule> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val props = item as? Map<String, Any> ?: return@mapNotNull null
            AIRule(
                condition = props["condition"] as? String ?: return@mapNotNull null,
                action = props["action"] as? String ?: return@mapNotNull null,
                priority = (props["priority"] as? Number)?.toInt() ?: 0
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRewards(raw: Any?): CombatRewards? {
        val map = raw as? Map<String, Any> ?: return null
        return CombatRewards(
            expFormula = map["expFormula"] as? String,
            drops = parseDrops(map["drops"])
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseDrops(raw: Any?): List<DropDefinition> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val props = item as? Map<String, Any> ?: return@mapNotNull null
            val itemId = props["item"] as? String ?: return@mapNotNull null
            val chance = RequiresParser.toDouble(props["chance"]) ?: 1.0
            val (qMin, qMax) = parseQuantity(props["quantity"])
            DropDefinition(item = itemId, chance = chance, quantityMin = qMin, quantityMax = qMax)
        }
    }

    /**
     * 解析数量字段，支持格式："3"（固定）或 "1-3"（范围）
     */
    private fun parseQuantity(raw: Any?): Pair<Int, Int> {
        val str = raw?.toString() ?: return Pair(1, 1)
        return if ("-" in str) {
            val parts = str.split("-")
            val min = parts[0].trim().toIntOrNull() ?: 1
            val max = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: min
            Pair(min, max)
        } else {
            val v = str.toIntOrNull() ?: 1
            Pair(v, v)
        }
    }
}

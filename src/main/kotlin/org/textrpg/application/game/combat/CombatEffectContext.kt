package org.textrpg.application.game.combat

import io.github.oshai.kotlinlogging.KotlinLogging
import org.textrpg.application.domain.model.CombatConfig
import org.textrpg.application.game.attribute.FormulaEngine
import org.textrpg.application.game.effect.EffectContext
import org.textrpg.application.game.effect.EntityAccessor
import kotlin.random.Random

private val log = KotlinLogging.logger {}

/**
 * 战斗特效执行上下文
 *
 * 支持全部 7 种目标选择器，以及战斗特有的公式前缀引用。
 * 替代 [SimpleEffectContext] 在战斗场景中使用。
 *
 * 目标选择器：
 * - `self` → 施法者
 * - `target` → 显式目标
 * - `all_enemies` → 对方全部存活实体
 * - `all_allies` → 己方全部存活实体
 * - `random_enemy` → 对方随机一个存活实体
 * - `random_ally` → 己方随机一个存活实体
 * - `all` → 所有存活实体
 *
 * 公式增强（在传入 [FormulaEngine] 前预处理）：
 * - `attacker.xxx` / `defender.xxx` / `self.xxx` / `target.xxx` → 属性前缀引用
 * - `random(N)` → 替换为 0..N-1 的随机数
 *
 * @param source 效果发起者
 * @param primaryTarget 显式目标
 * @param allies 施法者一方的全部实体
 * @param enemies 对方全部实体
 * @param combatConfig 战斗公式配置
 * @param random 随机数生成器（可注入以支持测试）
 */
class CombatEffectContext(
    override val source: EntityAccessor,
    override val primaryTarget: EntityAccessor?,
    private val allies: List<CombatEntity>,
    private val enemies: List<CombatEntity>,
    private val combatConfig: CombatConfig,
    private val random: Random = Random.Default
) : EffectContext {

    override fun resolveTargets(selector: String): List<EntityAccessor> {
        return when (selector) {
            "self" -> listOf(source)
            "target" -> listOfNotNull(primaryTarget)
            "all_enemies" -> enemies.filter { it.isAlive }
            "all_allies" -> allies.filter { it.isAlive }
            "random_enemy" -> enemies.filter { it.isAlive }.randomOrNull(random)?.let { listOf(it) } ?: emptyList()
            "random_ally" -> allies.filter { it.isAlive }.randomOrNull(random)?.let { listOf(it) } ?: emptyList()
            "all" -> (allies + enemies).filter { it.isAlive }
            else -> {
                log.warn { "Unknown target selector '$selector' in combat context" }
                emptyList()
            }
        }
    }

    override fun resolveFormula(formula: String, entity: EntityAccessor): Double {
        // 预处理 random(N)
        val processedFormula = preprocessRandom(formula)

        return FormulaEngine.evaluate(processedFormula) { key ->
            resolveAttribute(key, entity)
        }
    }

    /**
     * 解析带前缀的属性引用
     *
     * 支持 `attacker_xxx`、`defender_xxx`、`self_xxx`、`target_xxx` 前缀。
     * 无前缀时从默认实体解析。
     */
    private fun resolveAttribute(key: String, defaultEntity: EntityAccessor): Double {
        // 检查前缀映射
        val prefixMappings = mapOf(
            "attacker_" to source,
            "defender_" to primaryTarget,
            "self_" to source,
            "target_" to primaryTarget
        )

        for ((prefix, entity) in prefixMappings) {
            if (key.startsWith(prefix) && entity != null) {
                val attrKey = key.removePrefix(prefix)
                return entity.getAttributeValue(attrKey)
            }
        }

        // 无前缀：从默认实体解析
        return defaultEntity.getAttributeValue(key)
    }

    /**
     * 预处理公式中的 `random(N)` 调用
     *
     * 将 `random(N)` 替换为实际的随机数值。
     * 注意：每次调用 resolveFormula 时随机数重新生成。
     */
    private fun preprocessRandom(formula: String): String {
        return RANDOM_PATTERN.replace(formula) { matchResult ->
            val n = matchResult.groupValues[1].toIntOrNull() ?: 100
            random.nextInt(n).toString()
        }
    }

    /**
     * 预处理战斗公式中的点前缀（`attacker.xxx` → `attacker_xxx`）
     *
     * 由于 [FormulaEngine] 的 Tokenizer 将 `.` 作为小数点处理，
     * 带点的前缀（如 `attacker.physical_attack`）无法正确解析。
     * 此方法将点前缀转为下划线前缀。
     *
     * 使用单一正则一次替换全部 6 种前缀，避免 6 次字符串扫描。
     */
    fun preprocessCombatFormula(formula: String): String {
        return PREFIX_DOT.replace(formula) { match -> "${match.groupValues[1]}_" }
    }

    companion object {
        private val RANDOM_PATTERN = Regex("""random\((\d+)\)""")
        private val PREFIX_DOT = Regex("""\b(attacker|defender|self|target|enemy|player)\.""")
    }
}

package org.textrpg.application.data.config

import org.textrpg.application.domain.model.SkillDefinition
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * 技能配置加载结果
 *
 * @property skills 技能定义映射（skill_key -> [SkillDefinition]）
 */
data class SkillConfig(
    val skills: Map<String, SkillDefinition> = emptyMap()
)

/**
 * 技能配置加载器
 *
 * 从 YAML 文件加载技能定义，解析为不可变的 [SkillDefinition] 领域模型。
 * 复用 [RequiresParser] 解析前置条件和消耗，复用 [EffectConfigLoader] 解析效果列表。
 *
 * YAML 格式示例：
 * ```yaml
 * skills:
 *   fireball:
 *     displayName: "火球术"
 *     description: "向敌人投掷一个火球"
 *     requires:
 *       notHasBuff: "silenced"
 *     cost:
 *       mp: 25
 *     cooldown: 2
 *     effects:
 *       - type: "modify_attribute"
 *         target: "target"
 *         attribute: "current_hp"
 *         amount: "-(physical_attack * 1.5 + 30)"
 *       - type: "add_buff"
 *         target: "target"
 *         buffId: "burn_dot"
 *         duration: 3
 *     customScript: "scripts/fireball_special.kts"
 * ```
 *
 * @see SkillDefinition
 */
object SkillConfigLoader {
    private val yaml = Yaml()
    private const val DEFAULT_PATH = "src/main/resources/config/skills.yaml"

    /**
     * 从指定路径加载技能配置
     *
     * @param path YAML 配置文件路径（默认 [DEFAULT_PATH]）
     * @return [SkillConfig] 实例，加载失败时返回默认空配置
     */
    fun load(path: String = DEFAULT_PATH): SkillConfig {
        val file = File(path)
        if (!file.exists()) {
            println("Warning: Skill config not found at $path, using empty definitions")
            return SkillConfig()
        }
        return try {
            val raw = yaml.load<Map<String, Any>>(file.readText()) ?: return SkillConfig()
            @Suppress("UNCHECKED_CAST")
            val skillsRaw = raw["skills"] as? Map<String, Map<String, Any>> ?: emptyMap()
            val skills = skillsRaw.mapValues { (key, props) -> parseSkill(key, props) }
            SkillConfig(skills = skills)
        } catch (e: Exception) {
            println("Warning: Failed to load skill config from $path: ${e.message}")
            SkillConfig()
        }
    }

    /**
     * 解析单个技能定义
     */
    private fun parseSkill(key: String, props: Map<String, Any>): SkillDefinition {
        return SkillDefinition(
            key = key,
            displayName = props["displayName"] as? String ?: key,
            description = props["description"] as? String ?: "",
            requires = RequiresParser.parseRequires(props["requires"]),
            cost = RequiresParser.parseCost(props["cost"]),
            cooldown = (props["cooldown"] as? Number)?.toInt() ?: 0,
            effects = EffectConfigLoader.parseEffectList(props["effects"]),
            customScript = props["customScript"] as? String
        )
    }
}

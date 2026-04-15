package org.textrpg.application.data.config

import org.textrpg.application.domain.model.SkillDefinition

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
object SkillConfigLoader : AbstractYamlLoader<SkillConfig>() {
    override val defaultPath = "src/main/resources/config/skills.yaml"
    override val default = SkillConfig()
    override val configName = "Skill"

    override fun parse(raw: Map<String, Any>): SkillConfig {
        @Suppress("UNCHECKED_CAST")
        val skillsRaw = raw["skills"] as? Map<String, Map<String, Any>> ?: return SkillConfig()
        return SkillConfig(skills = skillsRaw.mapValues { (key, props) -> parseSkill(key, props) })
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

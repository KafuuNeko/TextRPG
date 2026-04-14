package org.textrpg.application.data.config

import org.textrpg.application.domain.model.AISafetyConfig
import org.textrpg.application.domain.model.AISceneConfig
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * AI 配置
 *
 * @property safety AI 安全限制配置
 * @property scenes AI 场景配置映射（场景名 → 配置）
 */
data class AIConfig(
    val safety: AISafetyConfig = AISafetyConfig(),
    val scenes: Map<String, AISceneConfig> = emptyMap()
)

/**
 * AI 配置加载器
 */
object AIConfigLoader {
    private val yaml = Yaml()
    private const val DEFAULT_PATH = "src/main/resources/config/ai.yaml"

    fun load(path: String = DEFAULT_PATH): AIConfig {
        val file = File(path)
        if (!file.exists()) {
            println("Warning: AI config not found at $path, using defaults")
            return AIConfig()
        }
        return try {
            val raw = yaml.load<Map<String, Any>>(file.readText()) ?: return AIConfig()
            @Suppress("UNCHECKED_CAST")
            val safety = parseSafety(raw["aiSafety"] as? Map<String, Any>)
            @Suppress("UNCHECKED_CAST")
            val scenes = parseScenes(raw["aiScenes"] as? Map<String, Map<String, Any>>)
            AIConfig(safety = safety, scenes = scenes)
        } catch (e: Exception) {
            println("Warning: Failed to load AI config from $path: ${e.message}")
            AIConfig()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSafety(raw: Map<String, Any>?): AISafetyConfig {
        if (raw == null) return AISafetyConfig()
        return AISafetyConfig(
            enabled = raw["enabled"] as? Boolean ?: true,
            presetSystemPrompt = raw["presetSystemPrompt"] as? Boolean ?: true,
            customSafetyRules = (raw["customSafetyRules"] as? List<*>)?.map { it.toString() } ?: emptyList()
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseScenes(raw: Map<String, Map<String, Any>>?): Map<String, AISceneConfig> {
        if (raw == null) return emptyMap()
        return raw.mapValues { (key, props) ->
            AISceneConfig(
                sceneName = key,
                allowedTools = (props["allowedTools"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                promptTemplate = props["promptTemplate"] as? String
            )
        }
    }
}

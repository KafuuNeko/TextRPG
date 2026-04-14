package org.textrpg.application.domain.model

/**
 * AI 安全配置
 *
 * 控制框架预置的安全限制。安全 Prompt 硬编码在框架源码中，
 * 配置只控制"是否启用"。即使删除 YAML 配置，默认行为仍然是启用。
 *
 * @property enabled 是否启用安全限制（默认 true）
 * @property presetSystemPrompt 是否注入框架预置的安全 System Prompt（默认 true）
 * @property customSafetyRules 设计者追加的自定义安全规则
 */
data class AISafetyConfig(
    val enabled: Boolean = true,
    val presetSystemPrompt: Boolean = true,
    val customSafetyRules: List<String> = emptyList()
)

/**
 * AI 场景配置
 *
 * 定义单个 AI 场景的工具白名单和 Prompt 模板。
 *
 * @property sceneName 场景名称
 * @property allowedTools 该场景允许 AI 调用的工具白名单
 * @property promptTemplate 场景 Prompt 模板（可含占位符）
 */
data class AISceneConfig(
    val sceneName: String,
    val allowedTools: List<String> = emptyList(),
    val promptTemplate: String? = null
)

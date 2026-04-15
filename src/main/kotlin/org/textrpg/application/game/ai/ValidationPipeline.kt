package org.textrpg.application.game.ai

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * 校验结果
 *
 * @property valid 是否通过所有校验
 * @property errors 校验错误列表
 */
data class ValidationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList()
) {
    companion object {
        val PASSED = ValidationResult(valid = true)
        fun failed(errors: List<String>) = ValidationResult(valid = false, errors = errors)
        fun failed(error: String) = ValidationResult(valid = false, errors = listOf(error))
    }
}

/**
 * AI 校验器接口
 *
 * 框架提供接口，范例层实现具体校验规则（数值边界、权限隔离等）。
 *
 * **数据约定**：[validate] 接收 `Map<String, Any?>` 作为校验数据载体，
 * 由调用方按本管道约定的字段名组织：
 * - `content: String` — 需要做 JSON 格式校验时放入原始 JSON 文本
 * - 其他领域字段由范例与自定义校验器自行约定
 */
interface AIValidator {

    /** 校验器名称（用于日志） */
    val name: String

    /**
     * 执行校验
     *
     * @param data 待校验的结构化数据（由调用方按约定组织 key）
     * @return 校验结果
     */
    fun validate(data: Map<String, Any?>): ValidationResult
}

/**
 * AI 数据校验管道
 *
 * 对 AI 输出进行多层校验：格式校验 → 字段校验 → 自定义校验。
 * 框架内置格式（[JsonFormatValidator]）和字段（[RequiredFieldValidator]）校验器，
 * 范例层通过 [addValidator] 注册额外规则。
 *
 * **定位**：本管道是**范例层可选工具**——框架层不在 `AISceneManager` 内部强制调用，
 * 因为不同 AI 场景对输出的校验需求差异极大（NPC 对话 vs. 世界生成）。
 * 范例层根据实际场景决定是否在 `AISceneManager.executeScene` 的返回结果上追加校验。
 *
 * **典型用法**：
 * ```kotlin
 * val pipeline = ValidationPipeline().apply {
 *     addValidator(JsonFormatValidator())
 *     addValidator(RequiredFieldValidator(listOf("name", "type")))
 *     addValidator(myCustomValidator)
 * }
 *
 * val result = pipeline.validate(mapOf(
 *     "content" to aiRawJson,
 *     "name" to parsedName,
 *     "type" to parsedType
 * ))
 * if (!result.valid) {
 *     // 重试 AI 请求 / 降级处理
 * }
 * ```
 */
class ValidationPipeline {

    private val validators: MutableList<AIValidator> = mutableListOf()

    /**
     * 添加校验器
     */
    fun addValidator(validator: AIValidator) {
        validators.add(validator)
    }

    /**
     * 执行完整校验管道
     *
     * 按注册顺序执行所有校验器，收集所有错误（非短路）——方便一次性返回全部问题，
     * 避免调用方反复重试才发现每一个校验失败。
     */
    fun validate(data: Map<String, Any?>): ValidationResult {
        val allErrors = mutableListOf<String>()
        for (validator in validators) {
            val result = validator.validate(data)
            if (!result.valid) {
                allErrors.addAll(result.errors.map { "[${validator.name}] $it" })
            }
        }
        return if (allErrors.isEmpty()) ValidationResult.PASSED
        else ValidationResult.failed(allErrors)
    }
}

/**
 * JSON 格式校验器（内置）
 *
 * 校验 `data["content"]` 是否为合法的 JSON 字符串。未提供 `content` 字段时直接通过
 * （校验是机会性的，调用方不必为每条数据都塞 content）。
 */
class JsonFormatValidator : AIValidator {
    override val name = "JsonFormat"
    private val gson = Gson()

    override fun validate(data: Map<String, Any?>): ValidationResult {
        val rawJson = data["content"] as? String ?: return ValidationResult.PASSED
        return runCatching { gson.fromJson(rawJson, Any::class.java) }
            .fold(
                onSuccess = { ValidationResult.PASSED },
                onFailure = { e ->
                    if (e is JsonSyntaxException) ValidationResult.failed("Invalid JSON: ${e.message}")
                    else ValidationResult.failed("JSON parse error: ${e.message}")
                }
            )
    }
}

/**
 * 必填字段校验器（内置）
 *
 * 校验数据中是否包含所有必填字段（非 null 视为已提供）。
 *
 * @param requiredFields 必填字段名列表
 */
class RequiredFieldValidator(
    private val requiredFields: List<String>
) : AIValidator {
    override val name = "RequiredFields"

    override fun validate(data: Map<String, Any?>): ValidationResult {
        val missing = requiredFields.filter { field ->
            !data.containsKey(field) || data[field] == null
        }
        return if (missing.isEmpty()) ValidationResult.PASSED
        else ValidationResult.failed("Missing required fields: ${missing.joinToString(", ")}")
    }
}

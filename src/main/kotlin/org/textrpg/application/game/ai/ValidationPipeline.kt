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
 */
interface AIValidator {

    /** 校验器名称（用于日志） */
    val name: String

    /**
     * 执行校验
     *
     * @param data AI 输出的结构化数据
     * @return 校验结果
     */
    fun validate(data: Map<String, Any?>): ValidationResult
}

/**
 * AI 数据校验管道
 *
 * 对 AI 输出进行多层校验：格式校验 → 字段校验 → 自定义校验。
 * 框架内置格式和字段校验器，范例层通过 [addValidator] 注册额外规则。
 *
 * 使用示例：
 * ```kotlin
 * val pipeline = ValidationPipeline()
 * pipeline.addValidator(RequiredFieldValidator(listOf("name", "type")))
 * pipeline.addValidator(myCustomValidator)
 *
 * val result = pipeline.validate(aiOutputData)
 * if (!result.valid) {
 *     // 重试 AI 请求
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
     * 按注册顺序执行所有校验器，收集所有错误。
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
 * 校验给定的字符串是否为合法的 JSON。
 */
class JsonFormatValidator : AIValidator {
    override val name = "JsonFormat"
    private val gson = Gson()

    override fun validate(data: Map<String, Any?>): ValidationResult {
        val rawJson = data["_raw_json"] as? String ?: return ValidationResult.PASSED
        return try {
            gson.fromJson(rawJson, Any::class.java)
            ValidationResult.PASSED
        } catch (e: JsonSyntaxException) {
            ValidationResult.failed("Invalid JSON: ${e.message}")
        }
    }
}

/**
 * 必填字段校验器（内置）
 *
 * 校验数据中是否包含所有必填字段。
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

package org.textrpg.application.domain.model

/**
 * 属性层级枚举
 *
 * 区分一级属性和二级属性。一级属性为纯存储值，不由公式计算，
 * 只能通过直接赋值或修正器修改；二级属性由公式从其他属性计算而来，支持嵌套引用。
 */
enum class AttributeTier(val value: Int) {
    /** 一级属性：纯存储值（如力量、敏捷、体质） */
    PRIMARY(1),
    /** 二级属性：由公式计算（如攻击力、暴击率、生命上限） */
    DERIVED(2);

    companion object {
        fun fromValue(value: Int): AttributeTier = entries.first { it.value == value }
    }
}

/**
 * 修正器类型枚举
 *
 * 决定修正器的计算方式。在默认的计算流程中，
 * 先累加所有 [FLAT] 修正器，再依次乘算 [PERCENT] 修正器。
 * 具体计算顺序可通过修正器的 priority 字段自定义。
 */
enum class ModifierType(val value: String) {
    /** 加算：直接加减固定值 */
    FLAT("flat"),
    /** 乘算：按百分比增减（值为百分比，如 10.0 表示 +10%） */
    PERCENT("percent");

    companion object {
        fun fromValue(value: String): ModifierType = entries.first { it.value == value }
    }
}

/**
 * 触发器类型枚举
 *
 * 定义属性值变化时可触发的事件类型。
 * 触发器的具体行为（内置行为或自定义脚本）在属性定义的 YAML 配置中指定。
 */
enum class TriggerType(val value: String) {
    /** 当前值降至下限（默认为 0）时触发 */
    ON_DEPLETE("onDeplete"),
    /** 当前值达到指定阈值时触发 */
    ON_THRESHOLD("onThreshold"),
    /** 当前值达到上限时触发 */
    ON_CAP("onCap"),
    /** 每次值变化时触发 */
    ON_CHANGE("onChange");

    companion object {
        fun fromValue(value: String): TriggerType = entries.first { it.value == value }
    }
}

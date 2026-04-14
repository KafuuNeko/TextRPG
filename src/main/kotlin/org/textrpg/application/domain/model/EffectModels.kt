package org.textrpg.application.domain.model

/**
 * 内置原子操作类型枚举
 *
 * 定义特效引擎提供的所有内置原子操作。设计者通过组合原子操作拼出复杂效果，
 * 脚本可通过 [EffectEngine.registerHandler] 注册自定义操作类型。
 *
 * 运行时派发基于字符串匹配（[EffectDefinition.type]），本枚举作为文档和类型安全的注册辅助。
 */
enum class BuiltinEffectType(val value: String) {
    /** 修改目标属性值（加减） */
    MODIFY_ATTRIBUTE("modify_attribute"),
    /** 直接设置目标属性值 */
    SET_ATTRIBUTE("set_attribute"),
    /** 挂载属性修正器 */
    ADD_MODIFIER("add_modifier"),
    /** 按来源移除属性修正器 */
    REMOVE_MODIFIER("remove_modifier"),
    /** 给目标添加 Buff */
    ADD_BUFF("add_buff"),
    /** 移除目标的 Buff */
    REMOVE_BUFF("remove_buff"),
    /** 传送到指定地图节点 */
    TELEPORT("teleport"),
    /** 向目标发送文本消息 */
    SEND_MESSAGE("send_message"),
    /** 给予物品 */
    GIVE_ITEM("give_item"),
    /** 移除物品 */
    REMOVE_ITEM("remove_item"),
    /** 启动会话（如进入战斗） */
    START_SESSION("start_session"),
    /** 执行自定义脚本 */
    RUN_SCRIPT("run_script");

    companion object {
        fun fromValue(value: String): BuiltinEffectType = entries.first { it.value == value }
    }
}

/**
 * 目标选择器枚举
 *
 * 定义特效引擎内置的目标选择方式。每个 Effect 通过 target 字段指定作用对象，
 * 引擎在运行时将选择器解析为具体的实体列表。
 *
 * 脚本可通过自定义 [EffectContext] 实现扩展选择器（如"HP 最低的友方"）。
 */
enum class TargetSelector(val value: String) {
    /** 施法者自身 */
    SELF("self"),
    /** 当前选中的目标 */
    TARGET("target"),
    /** 所有敌方 */
    ALL_ENEMIES("all_enemies"),
    /** 所有友方 */
    ALL_ALLIES("all_allies"),
    /** 随机一个敌方 */
    RANDOM_ENEMY("random_enemy"),
    /** 随机一个友方 */
    RANDOM_ALLY("random_ally"),
    /** 场上所有实体 */
    ALL("all");

    companion object {
        fun fromValue(value: String): TargetSelector = entries.first { it.value == value }
    }
}

/**
 * 特效定义（不可变领域模型）
 *
 * 从 YAML 配置加载的单个特效描述，是特效引擎的最小执行单元。
 * 不同的 [type] 使用不同的参数字段，未使用的字段保持默认值。
 *
 * 引擎不硬编码任何游戏概念，[type] 字段为字符串以支持自定义扩展。
 * 内置类型参见 [BuiltinEffectType]。
 *
 * @property type 效果类型标识（对应 [BuiltinEffectType.value] 或自定义类型）
 * @property target 目标选择器字符串（对应 [TargetSelector.value] 或自定义选择器）
 * @property attribute 目标属性 key（modify_attribute / set_attribute / add_modifier / remove_modifier 使用）
 * @property amount 数值变化量的公式字符串（modify_attribute 使用，支持属性引用和四则运算）
 * @property value 直接设置值的公式字符串（set_attribute 使用）
 * @property source 修正器来源标识（add_modifier / remove_modifier 使用）
 * @property modifierType 修正器类型："flat" 或 "percent"（add_modifier 使用）
 * @property modifierValue 修正器数值（add_modifier 使用）
 * @property modifierPriority 修正器优先级（add_modifier 使用）
 * @property buffId Buff 标识符（add_buff / remove_buff 使用）
 * @property stacks Buff 叠加层数（add_buff 使用）
 * @property duration Buff 持续回合数（add_buff 使用，null 表示永久）
 * @property nodeId 目标地图节点 ID（teleport 使用）
 * @property message 消息文本（send_message 使用，支持简单占位符）
 * @property itemTemplateId 物品模板 ID（give_item / remove_item 使用）
 * @property quantity 物品数量（give_item / remove_item 使用）
 * @property sessionType 会话类型标识（start_session 使用）
 * @property params 通用参数映射（start_session / 自定义效果使用）
 * @property scriptPath 脚本路径（run_script 使用）
 */
data class EffectDefinition(
    val type: String,
    val target: String = "self",
    val attribute: String? = null,
    val amount: String? = null,
    val value: String? = null,
    val source: String? = null,
    val modifierType: String? = null,
    val modifierValue: Double? = null,
    val modifierPriority: Int = 0,
    val buffId: String? = null,
    val stacks: Int = 1,
    val duration: Int? = null,
    val nodeId: String? = null,
    val message: String? = null,
    val itemTemplateId: String? = null,
    val quantity: Int = 1,
    val sessionType: String? = null,
    val params: Map<String, String> = emptyMap(),
    val scriptPath: String? = null
)

/**
 * 特效执行结果
 *
 * 单个原子操作的执行结果。特效引擎按顺序执行效果列表，
 * 单个效果失败时跳过并继续执行剩余效果，不回滚已执行的效果。
 *
 * @property success 是否执行成功
 * @property message 失败原因或执行反馈文本（成功时可为 null）
 */
data class EffectResult(
    val success: Boolean,
    val message: String? = null
) {
    companion object {
        val SUCCESS = EffectResult(success = true)
        fun success(message: String) = EffectResult(success = true, message = message)
        fun failed(message: String) = EffectResult(success = false, message = message)
    }
}

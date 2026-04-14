package org.textrpg.application.domain.model

/**
 * 指令定义（不可变领域模型）
 *
 * 从 YAML 配置加载的指令模板，定义指令的触发方式、前置条件、消耗和处理器。
 * 指令系统完全配置驱动，设计者通过 YAML 注册指令，引擎负责解析和路由。
 *
 * @property key 指令唯一标识符（对应 YAML 中的 key）
 * @property displayName 显示名称（用于帮助列表等）
 * @property aliases 别名列表，玩家可以用任意别名触发指令
 * @property description 指令说明文本
 * @property params 参数定义列表
 * @property handler 处理器标识：`"builtin:xxx"` 为内置处理器，`"script:xxx.kts"` 为脚本处理器
 * @property requires 前置条件（可选），不满足时拒绝执行并返回提示
 * @property cost 属性消耗映射（attribute_key -> 消耗量），执行前扣除
 */
data class CommandDefinition(
    val key: String,
    val displayName: String = "",
    val aliases: List<String> = emptyList(),
    val description: String = "",
    val params: List<ParamDefinition> = emptyList(),
    val handler: String,
    val requires: RequiresDefinition = RequiresDefinition(),
    val cost: Map<String, Double> = emptyMap()
)

/**
 * 指令参数定义
 *
 * @property name 参数名称
 * @property type 参数类型（"string" / "int" / "double"）
 * @property required 是否为必填参数
 */
data class ParamDefinition(
    val name: String,
    val type: String = "string",
    val required: Boolean = false
)

/**
 * 前置条件定义（Requires）
 *
 * 指令和技能共用的统一前置条件检查。所有字段均为可选，
 * 为 null 表示不检查该项。可通过脚本扩展自定义检查条件。
 *
 * @property registered 是否要求已注册角色
 * @property minAttribute 属性最低值要求（attribute_key -> 最低值）
 * @property inSession 要求处于指定类型的会话中
 * @property notInSession 要求不在任何会话中
 * @property atNodeTag 要求当前地图节点拥有指定标签
 * @property hasItem 要求拥有指定物品（item_id -> 数量）
 * @property hasBuff 要求拥有指定 Buff
 * @property notHasBuff 要求不拥有指定 Buff
 */
data class RequiresDefinition(
    val registered: Boolean? = null,
    val minAttribute: Map<String, Double>? = null,
    val inSession: String? = null,
    val notInSession: Boolean? = null,
    val atNodeTag: String? = null,
    val hasItem: Map<String, Int>? = null,
    val hasBuff: String? = null,
    val notHasBuff: String? = null
)

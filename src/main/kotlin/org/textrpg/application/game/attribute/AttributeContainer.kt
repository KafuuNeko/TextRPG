package org.textrpg.application.game.attribute

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.textrpg.application.domain.model.AttributeDefinition
import org.textrpg.application.domain.model.AttributeTier
import org.textrpg.application.domain.model.Modifier
import org.textrpg.application.domain.model.TriggerType

/**
 * 属性容器
 *
 * 管理一个游戏实体（玩家 / NPC / 怪物）的所有属性实例。
 * 提供属性查询与修改的统一入口，处理跨属性约束（boundMax）、
 * 二级属性自动重算和触发器事件分发。
 *
 * 每次值变化的完整流程：
 * 1. 快照所有属性的旧值
 * 2. 应用修改（直接赋值或修正器变化）
 * 3. 重算所有二级属性（按拓扑排序）
 * 4. 对比新旧值，检查触发条件，分发触发事件
 *
 * @param definitions 属性定义映射，来自 YAML 配置加载
 */
class AttributeContainer(private val definitions: Map<String, AttributeDefinition>) {

    /** 属性实例映射，保持插入顺序（便于调试和遍历） */
    private val instances: LinkedHashMap<String, AttributeInstance> = LinkedHashMap()

    /**
     * 二级属性的求值顺序（拓扑排序）
     *
     * 保证依赖关系中，被引用的属性一定排在引用者前面。
     */
    private val derivedOrder: List<String>

    /**
     * 触发器处理器（可选）
     *
     * 由游戏层设置，接收属性变化触发的事件。未设置时触发器静默跳过。
     *
     * ```kotlin
     * container.triggerHandler = TriggerHandler { event ->
     *     println("${event.attributeKey} triggered ${event.triggerType}")
     * }
     * ```
     */
    var triggerHandler: TriggerHandler? = null

    init {
        // 初始化所有属性实例
        definitions.forEach { (key, def) ->
            instances[key] = AttributeInstance(def)
        }

        // 计算二级属性的拓扑排序
        derivedOrder = computeDerivedOrder()

        // 初始化时求值所有二级属性（不触发触发器）
        recalculateAllDerived()
    }

    // ======================== 值查询 ========================

    /**
     * 获取属性最终有效值（含修正器 + boundMax 约束）
     *
     * 这是外部消费者应使用的主要接口。返回值经过完整的计算流程：
     * 1. 基础值 + 修正器计算（由 [AttributeInstance] 处理）
     * 2. boundMax 跨属性裁剪（如 current_hp 不超过 max_hp）
     *
     * @param key 属性标识符
     * @return 最终有效值，属性不存在时返回 0.0
     */
    fun getValue(key: String): Double {
        val instance = instances[key] ?: return 0.0
        var value = instance.getFinalValue()

        // 跨属性上限约束：如果定义了 boundMax，裁剪到目标属性的当前值
        val boundMaxKey = instance.definition.boundMax
        if (boundMaxKey != null) {
            val maxValue = instances[boundMaxKey]?.getFinalValue() ?: Double.MAX_VALUE
            value = value.coerceAtMost(maxValue)
        }

        return value
    }

    /**
     * 获取属性基础值（不含修正器和跨属性约束）
     *
     * @param key 属性标识符
     * @return 基础值，属性不存在时返回 0.0
     */
    fun getBaseValue(key: String): Double =
        instances[key]?.getBaseValue() ?: 0.0

    /**
     * 获取属性实例（供引擎内部使用）
     *
     * 外部逻辑应优先使用 [getValue] 获取有效值，
     * 直接操作实例时不会触发跨属性约束和触发器。
     *
     * @param key 属性标识符
     * @return 属性实例，不存在时返回 null
     */
    fun getInstance(key: String): AttributeInstance? = instances[key]

    /**
     * 检查是否包含指定属性
     */
    fun contains(key: String): Boolean = instances.containsKey(key)

    /**
     * 获取所有属性标识符
     */
    fun getAllKeys(): Set<String> = instances.keys.toSet()

    // ======================== 值修改 ========================

    /**
     * 设置属性基础值
     *
     * 设置后自动重算二级属性并检查触发器。
     *
     * @param key 属性标识符
     * @param value 新的基础值
     */
    fun setBaseValue(key: String, value: Double) {
        val instance = instances[key] ?: return
        withTriggerCheck {
            instance.setBaseValue(value)
            recalculateAllDerived()
        }
    }

    /**
     * 增减属性基础值
     *
     * 在当前基础值上加减 delta，适用于伤害、治疗、经验获取等场景。
     *
     * @param key 属性标识符
     * @param delta 变化量（正数增加，负数减少）
     */
    fun modifyBaseValue(key: String, delta: Double) {
        val instance = instances[key] ?: return
        withTriggerCheck {
            instance.setBaseValue(instance.getBaseValue() + delta)
            recalculateAllDerived()
        }
    }

    // ======================== 修正器操作 ========================

    /**
     * 为指定属性添加修正器
     *
     * @param key 属性标识符
     * @param modifier 修正器实例
     */
    fun addModifier(key: String, modifier: Modifier) {
        val instance = instances[key] ?: return
        withTriggerCheck {
            instance.addModifier(modifier)
            recalculateAllDerived()
        }
    }

    /**
     * 移除指定属性上来自特定来源的所有修正器
     *
     * @param key 属性标识符
     * @param source 来源标识
     * @return 被移除的修正器数量
     */
    fun removeModifiersBySource(key: String, source: String): Int {
        val instance = instances[key] ?: return 0
        var removed = 0
        withTriggerCheck {
            removed = instance.removeModifiersBySource(source)
            if (removed > 0) recalculateAllDerived()
        }
        return removed
    }

    /**
     * 移除所有属性上来自特定来源的修正器
     *
     * 适用于装备脱下（移除该装备的所有加成）或 Buff 消失等场景。
     *
     * @param source 来源标识（如 "equipment:slot_weapon"）
     */
    fun removeAllModifiersBySource(source: String) {
        withTriggerCheck {
            var anyRemoved = false
            instances.values.forEach {
                if (it.removeModifiersBySource(source) > 0) anyRemoved = true
            }
            if (anyRemoved) recalculateAllDerived()
        }
    }

    /**
     * 清空指定属性的所有修正器
     *
     * @param key 属性标识符
     */
    fun clearModifiers(key: String) {
        withTriggerCheck {
            instances[key]?.clearModifiers()
            recalculateAllDerived()
        }
    }

    // ======================== 快照与持久化 ========================

    /**
     * 生成所有属性的快照（属性 key -> 最终有效值）
     *
     * 可用于战斗日志、存档、AI 上下文构建等场景。
     */
    fun snapshot(): Map<String, Double> =
        instances.keys.associateWith { getValue(it) }

    /**
     * 将所有属性基础值序列化为 JSON
     *
     * 用于持久化到数据库。仅存储 baseValue，修正器由装备/Buff 重新挂载。
     *
     * @return JSON 字符串，如 {"strength": 15, "current_hp": 80}
     */
    fun serializeBaseValues(): String {
        val data = instances.entries.associate { (key, instance) -> key to instance.getBaseValue() }
        return Gson().toJson(data)
    }

    /**
     * 从 JSON 恢复属性基础值
     *
     * 将 JSON 中的每个 key-value 对应用到已存在的属性实例上。
     * 未知的 key 静默忽略，未出现的 key 保持默认值。
     * 恢复后自动重算所有二级属性。
     *
     * @param json JSON 字符串，如 {"strength": 15, "current_hp": 80}
     */
    fun deserializeBaseValues(json: String) {
        if (json.isBlank() || json == "{}") return
        try {
            val type = object : TypeToken<Map<String, Double>>() {}.type
            val data: Map<String, Double> = Gson().fromJson(json, type)
            for ((key, value) in data) {
                instances[key]?.setBaseValue(value)
            }
            recalculateAllDerived()
        } catch (e: Exception) {
            println("Warning: Failed to deserialize attribute data: ${e.message}")
        }
    }

    // ======================== 触发器系统 ========================

    /**
     * 包装修改操作：快照旧值 → 执行修改 → 检查并分发触发器
     *
     * @param action 修改操作（在旧值快照之后执行）
     */
    private inline fun withTriggerCheck(action: () -> Unit) {
        val handler = triggerHandler
        if (handler == null) {
            // 无处理器时跳过触发器逻辑，减少开销
            action()
            return
        }

        // 快照所有属性的变更前有效值
        val oldValues = instances.keys.associateWith { getValue(it) }

        // 执行修改
        action()

        // 检查触发条件并分发事件
        checkAndFireTriggers(oldValues, handler)
    }

    /**
     * 逐个属性检查触发条件，满足时分发事件
     *
     * 触发条件：
     * - [TriggerType.ON_CHANGE]：值发生任何变化
     * - [TriggerType.ON_DEPLETE]：值从正数降至属性下限（跨越检测）
     * - [TriggerType.ON_CAP]：值从非满升至属性上限（跨越检测）
     * - [TriggerType.ON_THRESHOLD]：值跨越指定阈值（双向检测）
     */
    private fun checkAndFireTriggers(oldValues: Map<String, Double>, handler: TriggerHandler) {
        for ((key, instance) in instances) {
            val triggers = instance.definition.triggers
            if (triggers.isEmpty()) continue

            val oldValue = oldValues[key] ?: 0.0
            val newValue = getValue(key)
            if (oldValue == newValue) continue

            // onChange：只要值变化就触发
            triggers[TriggerType.ON_CHANGE]?.let { def ->
                handler.onTrigger(TriggerEvent(key, TriggerType.ON_CHANGE, def, oldValue, newValue))
            }

            // onDeplete：从高于下限降至下限（跨越检测，防止重复触发）
            triggers[TriggerType.ON_DEPLETE]?.let { def ->
                val minBound = instance.definition.min
                if (oldValue > minBound && newValue <= minBound) {
                    handler.onTrigger(TriggerEvent(key, TriggerType.ON_DEPLETE, def, oldValue, newValue))
                }
            }

            // onCap：从低于上限升至上限（跨越检测）
            triggers[TriggerType.ON_CAP]?.let { def ->
                val maxBound = instance.definition.max
                if (oldValue < maxBound && newValue >= maxBound) {
                    handler.onTrigger(TriggerEvent(key, TriggerType.ON_CAP, def, oldValue, newValue))
                }
            }

            // onThreshold：跨越阈值（双向检测）
            triggers[TriggerType.ON_THRESHOLD]?.let { def ->
                val thresholdExpr = def.value ?: return@let
                val threshold = FormulaEngine.evaluate(thresholdExpr) { refKey -> getValue(refKey) }
                val crossedDown = oldValue > threshold && newValue <= threshold
                val crossedUp = oldValue < threshold && newValue >= threshold
                if (crossedDown || crossedUp) {
                    handler.onTrigger(TriggerEvent(key, TriggerType.ON_THRESHOLD, def, oldValue, newValue))
                }
            }
        }
    }

    // ======================== 二级属性公式求值 ========================

    /**
     * 重算所有二级属性
     *
     * 按拓扑排序依次求值，保证被引用的属性先于引用者计算。
     * 公式中的属性引用通过 [getValue] 解析，因此包含修正器和 boundMax 约束。
     */
    private fun recalculateAllDerived() {
        for (key in derivedOrder) {
            val instance = instances[key] ?: continue
            val formula = instance.definition.formula ?: continue
            val result = FormulaEngine.evaluate(formula) { refKey -> getValue(refKey) }
            instance.setBaseValue(result)
        }
    }

    /**
     * 计算二级属性的拓扑排序（Kahn 算法）
     *
     * 从公式中提取属性引用，构建依赖图，
     * 确保每个二级属性在其依赖的属性之后求值。
     * 检测到循环依赖时输出警告并跳过涉及循环的属性。
     *
     * @return 按依赖顺序排列的二级属性 key 列表
     */
    private fun computeDerivedOrder(): List<String> {
        val derivedKeys = definitions.filter { (_, def) ->
            def.tier == AttributeTier.DERIVED && def.formula != null
        }.keys

        if (derivedKeys.isEmpty()) return emptyList()

        // 每个二级属性依赖哪些（其他二级）属性
        val dependsOn = mutableMapOf<String, Set<String>>()
        derivedKeys.forEach { key ->
            val formula = definitions[key]!!.formula!!
            val refs = FormulaEngine.extractReferences(formula)
            dependsOn[key] = refs.intersect(derivedKeys)
        }

        // Kahn 算法：按入度逐层剥离
        val inDegree = derivedKeys.associateWith { key ->
            dependsOn[key]?.size ?: 0
        }.toMutableMap()

        val queue = ArrayDeque<String>()
        inDegree.filter { it.value == 0 }.keys.forEach { queue.add(it) }

        val sorted = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val key = queue.removeFirst()
            sorted.add(key)

            derivedKeys.filter { otherKey ->
                dependsOn[otherKey]?.contains(key) == true
            }.forEach { dependent ->
                inDegree[dependent] = (inDegree[dependent] ?: 1) - 1
                if (inDegree[dependent] == 0) {
                    queue.add(dependent)
                }
            }
        }

        if (sorted.size < derivedKeys.size) {
            val cyclic = derivedKeys - sorted.toSet()
            println("Warning: Circular dependency detected in derived attributes: $cyclic. These attributes will not be auto-calculated.")
        }

        return sorted
    }
}

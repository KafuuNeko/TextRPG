package org.textrpg.application.game.effect

import org.textrpg.application.domain.model.BuiltinEffectType
import org.textrpg.application.domain.model.EffectDefinition
import org.textrpg.application.domain.model.EffectResult
import org.textrpg.application.domain.model.Modifier
import org.textrpg.application.domain.model.ModifierType
import org.textrpg.application.utils.script.KotlinScriptRunner
import java.io.File

/**
 * 内置原子操作处理器工厂
 *
 * 为 [BuiltinEffectType] 中定义的 12 种内置原子操作创建对应的 [AtomicEffectHandler]。
 * 每个处理器实现一种最底层的"动词"操作，设计者通过组合这些原子操作拼出复杂效果。
 *
 * 尚未实现后端系统的操作（teleport、start_session）返回提示性结果，
 * 待对应系统（地图、战斗）实现后自动生效。
 *
 * @see EffectEngine
 * @see BuiltinEffectType
 */
object BuiltinEffectHandlers {

    /**
     * 创建所有内置处理器映射
     *
     * @param scriptRunner Kotlin 脚本执行器（可选，为 null 时 run_script 效果将返回失败）
     * @return 效果类型字符串 → 处理器的映射
     */
    fun createAll(scriptRunner: KotlinScriptRunner? = null): Map<String, AtomicEffectHandler> {
        return mapOf(
            BuiltinEffectType.MODIFY_ATTRIBUTE.value to modifyAttribute(),
            BuiltinEffectType.SET_ATTRIBUTE.value to setAttribute(),
            BuiltinEffectType.ADD_MODIFIER.value to addModifier(),
            BuiltinEffectType.REMOVE_MODIFIER.value to removeModifier(),
            BuiltinEffectType.ADD_BUFF.value to addBuff(),
            BuiltinEffectType.REMOVE_BUFF.value to removeBuff(),
            BuiltinEffectType.TELEPORT.value to teleport(),
            BuiltinEffectType.SEND_MESSAGE.value to sendMessage(),
            BuiltinEffectType.GIVE_ITEM.value to giveItem(),
            BuiltinEffectType.REMOVE_ITEM.value to removeItem(),
            BuiltinEffectType.START_SESSION.value to startSession(),
            BuiltinEffectType.RUN_SCRIPT.value to runScript(scriptRunner)
        )
    }

    /**
     * modify_attribute：修改目标属性值（加减）
     *
     * 必需参数：attribute, amount（公式字符串）
     * amount 公式在施法者（source）的属性上下文中求值
     */
    private fun modifyAttribute() = AtomicEffectHandler { effect, target, context ->
        val attribute = effect.attribute
            ?: return@AtomicEffectHandler EffectResult.failed("modify_attribute: missing 'attribute'")
        val amountFormula = effect.amount
            ?: return@AtomicEffectHandler EffectResult.failed("modify_attribute: missing 'amount'")

        try {
            val amount = context.resolveFormula(amountFormula, context.source)
            target.modifyAttribute(attribute, amount)
            EffectResult.success("$attribute ${if (amount >= 0) "+" else ""}$amount")
        } catch (e: Exception) {
            EffectResult.failed("modify_attribute: formula error: ${e.message}")
        }
    }

    /**
     * set_attribute：直接设置目标属性值
     *
     * 必需参数：attribute, value（公式字符串）
     */
    private fun setAttribute() = AtomicEffectHandler { effect, target, context ->
        val attribute = effect.attribute
            ?: return@AtomicEffectHandler EffectResult.failed("set_attribute: missing 'attribute'")
        val valueFormula = effect.value
            ?: return@AtomicEffectHandler EffectResult.failed("set_attribute: missing 'value'")

        try {
            val value = context.resolveFormula(valueFormula, context.source)
            target.setAttribute(attribute, value)
            EffectResult.success("$attribute = $value")
        } catch (e: Exception) {
            EffectResult.failed("set_attribute: formula error: ${e.message}")
        }
    }

    /**
     * add_modifier：挂载属性修正器
     *
     * 必需参数：attribute, source, modifierType, modifierValue
     */
    private fun addModifier() = AtomicEffectHandler { effect, target, _ ->
        val attribute = effect.attribute
            ?: return@AtomicEffectHandler EffectResult.failed("add_modifier: missing 'attribute'")
        val source = effect.source
            ?: return@AtomicEffectHandler EffectResult.failed("add_modifier: missing 'source'")
        val modType = effect.modifierType
            ?: return@AtomicEffectHandler EffectResult.failed("add_modifier: missing 'modifierType'")
        val modValue = effect.modifierValue
            ?: return@AtomicEffectHandler EffectResult.failed("add_modifier: missing 'modifierValue'")

        try {
            val modifier = Modifier(
                source = source,
                type = ModifierType.fromValue(modType),
                value = modValue,
                priority = effect.modifierPriority
            )
            target.addModifier(attribute, modifier)
            EffectResult.success("modifier added: $source -> $attribute ($modType $modValue)")
        } catch (e: Exception) {
            EffectResult.failed("add_modifier: ${e.message}")
        }
    }

    /**
     * remove_modifier：按来源移除修正器
     *
     * 必需参数：attribute, source
     */
    private fun removeModifier() = AtomicEffectHandler { effect, target, _ ->
        val attribute = effect.attribute
            ?: return@AtomicEffectHandler EffectResult.failed("remove_modifier: missing 'attribute'")
        val source = effect.source
            ?: return@AtomicEffectHandler EffectResult.failed("remove_modifier: missing 'source'")

        target.removeModifiersBySource(attribute, source)
        EffectResult.success("modifiers removed: $source from $attribute")
    }

    /**
     * add_buff：给目标添加 Buff
     *
     * 必需参数：buffId
     * 可选参数：stacks（默认 1）, duration（默认 null = 永久）
     */
    private fun addBuff() = AtomicEffectHandler { effect, target, _ ->
        val buffId = effect.buffId
            ?: return@AtomicEffectHandler EffectResult.failed("add_buff: missing 'buffId'")

        target.addBuff(buffId, effect.stacks, effect.duration)
        EffectResult.success("buff added: $buffId (stacks=${effect.stacks}, duration=${effect.duration ?: "permanent"})")
    }

    /**
     * remove_buff：移除目标的 Buff
     *
     * 必需参数：buffId
     */
    private fun removeBuff() = AtomicEffectHandler { effect, target, _ ->
        val buffId = effect.buffId
            ?: return@AtomicEffectHandler EffectResult.failed("remove_buff: missing 'buffId'")

        target.removeBuff(buffId)
        EffectResult.success("buff removed: $buffId")
    }

    /**
     * teleport：传送到指定地图节点
     *
     * 必需参数：nodeId
     * 注意：地图系统在 Step 6 实现前，此操作返回暂未实现的提示
     */
    private fun teleport() = AtomicEffectHandler { effect, _, _ ->
        val nodeId = effect.nodeId
            ?: return@AtomicEffectHandler EffectResult.failed("teleport: missing 'nodeId'")

        // 地图系统尚未实现（Step 6）
        EffectResult.failed("teleport: map system not yet implemented (target: $nodeId)")
    }

    /**
     * send_message：向目标发送文本消息
     *
     * 必需参数：message
     */
    private fun sendMessage() = AtomicEffectHandler { effect, target, _ ->
        val message = effect.message
            ?: return@AtomicEffectHandler EffectResult.failed("send_message: missing 'message'")

        target.sendMessage(message)
        EffectResult.success("message sent")
    }

    /**
     * give_item：给予物品
     *
     * 必需参数：itemTemplateId
     * 可选参数：quantity（默认 1）
     */
    private fun giveItem() = AtomicEffectHandler { effect, target, _ ->
        val itemId = effect.itemTemplateId
            ?: return@AtomicEffectHandler EffectResult.failed("give_item: missing 'itemTemplateId'")

        target.giveItem(itemId, effect.quantity)
        EffectResult.success("item given: $itemId x${effect.quantity}")
    }

    /**
     * remove_item：移除物品
     *
     * 必需参数：itemTemplateId
     * 可选参数：quantity（默认 1）
     */
    private fun removeItem() = AtomicEffectHandler { effect, target, _ ->
        val itemId = effect.itemTemplateId
            ?: return@AtomicEffectHandler EffectResult.failed("remove_item: missing 'itemTemplateId'")

        target.removeItem(itemId, effect.quantity)
        EffectResult.success("item removed: $itemId x${effect.quantity}")
    }

    /**
     * start_session：启动会话（如进入战斗）
     *
     * 必需参数：sessionType
     * 注意：战斗系统在 Step 5 实现前，此操作返回暂未实现的提示
     */
    private fun startSession() = AtomicEffectHandler { effect, _, _ ->
        val sessionType = effect.sessionType
            ?: return@AtomicEffectHandler EffectResult.failed("start_session: missing 'sessionType'")

        // 会话启动逻辑在 Step 5 实现
        EffectResult.failed("start_session: session system not yet integrated (type: $sessionType)")
    }

    /**
     * run_script：执行自定义 Kotlin 脚本
     *
     * 必需参数：scriptPath
     * 脚本文件从项目根目录下的 scripts/ 目录加载
     */
    private fun runScript(scriptRunner: KotlinScriptRunner?) = AtomicEffectHandler { effect, target, context ->
        val scriptPath = effect.scriptPath
            ?: return@AtomicEffectHandler EffectResult.failed("run_script: missing 'scriptPath'")

        if (scriptRunner == null) {
            return@AtomicEffectHandler EffectResult.failed("run_script: script runner not available")
        }

        val scriptFile = File(scriptPath)
        if (!scriptFile.exists()) {
            return@AtomicEffectHandler EffectResult.failed("run_script: script not found: $scriptPath")
        }

        val scriptCode = scriptFile.readText()
        val scriptContext = mapOf<String, Any?>(
            "source" to context.source,
            "target" to target,
            "context" to context,
            "params" to effect.params
        )

        val result = scriptRunner.executeScript(scriptCode, scriptContext)
        if (result.success) {
            EffectResult.success("script executed: ${result.message}")
        } else {
            EffectResult.failed("run_script: ${result.message}")
        }
    }
}

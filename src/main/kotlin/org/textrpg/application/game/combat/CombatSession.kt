package org.textrpg.application.game.combat

import kotlinx.coroutines.*
import org.textrpg.application.domain.model.CombatConfig
import org.textrpg.application.domain.model.EnemyDefinition
import org.textrpg.application.game.attribute.FormulaEngine
import org.textrpg.application.game.command.Session
import org.textrpg.application.game.command.SessionManager
import org.textrpg.application.game.effect.EffectEngine
import org.textrpg.application.game.skill.SkillEngine
import kotlin.random.Random

/**
 * 战斗结局
 */
enum class CombatOutcome {
    /** 战斗继续 */
    CONTINUE,
    /** 玩家胜利 */
    PLAYER_WIN,
    /** 玩家失败 */
    PLAYER_LOSE,
    /** 玩家逃跑 */
    PLAYER_FLEE
}

/**
 * 战斗会话
 *
 * 实现 [Session] 接口的纯回合制战斗系统。内部启动协程运行战斗循环，
 * 需要玩家输入时 suspend 在 [CompletableDeferred] 上，
 * [handleMessage] 被调用时 complete 它来恢复循环。
 *
 * 战斗消息通过 [CombatEntity.sendMessage] 直接发送，[handleMessage] 返回 null。
 *
 * 回合流程：
 * ```
 * 回合开始 → 玩家行动（suspend 等待输入）→ 胜负检查
 *          → 敌人行动（AI 决策）→ 胜负检查
 *          → 回合结束（Buff tick + CD tick）→ 胜负检查
 * ```
 *
 * @param playerId 玩家 ID
 * @param playerEntity 玩家参战实体
 * @param enemyEntity 敌人参战实体
 * @param enemyDefinition 敌人定义（含 AI 规则和奖励）
 * @param combatConfig 战斗全局配置
 * @param skillEngine 技能引擎
 * @param effectEngine 特效引擎
 * @param enemyAI AI 决策引擎
 * @param sessionManager 会话管理器（战斗结束时注销）
 * @param coroutineScope 协程作用域
 */
class CombatSession(
    override val playerId: String,
    private val playerEntity: CombatEntity,
    private val enemyEntity: CombatEntity,
    private val enemyDefinition: EnemyDefinition,
    private val combatConfig: CombatConfig,
    private val skillEngine: SkillEngine,
    private val effectEngine: EffectEngine,
    private val enemyAI: EnemyAI,
    private val sessionManager: SessionManager,
    private val coroutineScope: CoroutineScope
) : Session {

    override val type: String = "combat"

    private var active = true
    private var inputDeferred: CompletableDeferred<String>? = null
    private var combatJob: Job? = null

    /** 玩家操作菜单（按索引对应） */
    private var currentMenu: List<MenuEntry> = emptyList()

    /**
     * 启动战斗循环
     *
     * 在协程中运行完整的战斗流程。战斗结束后自动注销会话。
     */
    fun start() {
        combatJob = coroutineScope.launch {
            try {
                runCombatLoop()
            } catch (e: CancellationException) {
                throw e // 不吞掉取消异常
            } catch (e: Exception) {
                playerEntity.sendMessage("战斗异常终止：${e.message}")
            } finally {
                active = false
                sessionManager.endSession(playerId)
            }
        }
    }

    override fun isActive(): Boolean = active

    override suspend fun handleMessage(message: String): String? {
        inputDeferred?.complete(message)
        return null // 战斗消息由协程内部通过 sendMessage 发送
    }

    override suspend fun onTimeout() {
        inputDeferred?.complete("__timeout__")
    }

    // ======================== 战斗主循环 ========================

    private suspend fun runCombatLoop() {
        playerEntity.sendMessage("⚔ 战斗开始！对手：${enemyEntity.displayName}")
        playerEntity.sendMessage(formatEnemyStatus())

        var roundNumber = 1
        while (true) {
            val outcome = runRound(roundNumber++)
            when (outcome) {
                CombatOutcome.CONTINUE -> continue
                CombatOutcome.PLAYER_WIN -> {
                    playerEntity.sendMessage("🏆 战斗胜利！击败了 ${enemyEntity.displayName}！")
                    settlement()
                    break
                }
                CombatOutcome.PLAYER_LOSE -> {
                    playerEntity.sendMessage("💀 战斗失败……")
                    break
                }
                CombatOutcome.PLAYER_FLEE -> {
                    playerEntity.sendMessage("🏃 成功逃离了战斗！")
                    break
                }
            }
        }
    }

    /**
     * 执行单回合
     */
    private suspend fun runRound(roundNumber: Int): CombatOutcome {
        playerEntity.sendMessage("\n--- 第 ${roundNumber} 回合 ---")

        // 玩家回合
        playerTurn()
        val check1 = checkWinLose()
        if (check1 != CombatOutcome.CONTINUE) return check1

        // 敌人回合
        enemyTurn(roundNumber)
        val check2 = checkWinLose()
        if (check2 != CombatOutcome.CONTINUE) return check2

        // 回合结束：Buff tick + CD tick
        roundEndPhase()
        return checkWinLose()
    }

    // ======================== 玩家回合 ========================

    private suspend fun playerTurn() {
        // 构建菜单
        val menu = buildPlayerMenu()
        currentMenu = menu

        val menuText = buildString {
            appendLine("你的回合，请选择行动：")
            menu.forEachIndexed { index, entry ->
                appendLine("  ${index + 1}. ${entry.display}")
            }
        }
        playerEntity.sendMessage(menuText)

        // 等待输入
        val input = awaitPlayerInput(combatConfig.defaultTimeoutSeconds * 1000)

        if (input == "__timeout__") {
            playerEntity.sendMessage("⏰ 超时，自动防御。")
            return
        }

        // 解析输入
        val action = parsePlayerInput(input)
        if (action == null) {
            playerEntity.sendMessage("无效输入，本回合跳过。")
            return
        }

        // 执行行动
        executePlayerAction(action)
    }

    private fun buildPlayerMenu(): List<MenuEntry> {
        val menu = mutableListOf<MenuEntry>()

        // 普通攻击（始终可用）
        menu.add(MenuEntry("攻击", MenuActionType.ATTACK))

        // 可用技能
        for (skillId in playerEntity.skillIds) {
            val skill = skillEngine.getSkill(skillId) ?: continue
            val onCd = playerEntity.cooldownManager.isOnCooldown(playerEntity.entityId, skillId)
            if (!onCd) {
                menu.add(MenuEntry(skill.displayName.ifEmpty { skillId }, MenuActionType.SKILL, skillId))
            } else {
                val remaining = playerEntity.cooldownManager.getCooldown(playerEntity.entityId, skillId)
                menu.add(MenuEntry("${skill.displayName.ifEmpty { skillId }}（冷却${remaining}回合）", MenuActionType.DISABLED))
            }
        }

        // 防御 & 逃跑
        menu.add(MenuEntry("防御", MenuActionType.DEFEND))
        if (combatConfig.fleeCheck != null) {
            menu.add(MenuEntry("逃跑", MenuActionType.FLEE))
        }

        return menu
    }

    private fun parsePlayerInput(input: String): MenuEntry? {
        // 尝试按数字索引
        val index = input.trim().toIntOrNull()
        if (index != null && index in 1..currentMenu.size) {
            val entry = currentMenu[index - 1]
            if (entry.actionType == MenuActionType.DISABLED) return null
            return entry
        }

        // 尝试按名称匹配
        return currentMenu.find {
            it.actionType != MenuActionType.DISABLED && it.display.contains(input.trim(), ignoreCase = true)
        }
    }

    private suspend fun executePlayerAction(action: MenuEntry) {
        when (action.actionType) {
            MenuActionType.ATTACK -> {
                // 普通攻击：使用战斗公式直接计算
                val damage = calculateDamage(playerEntity, enemyEntity)
                enemyEntity.modifyAttribute("current_hp", -damage)
                playerEntity.sendMessage("你发起攻击，造成 ${damage.toInt()} 点伤害！")
                playerEntity.sendMessage(formatEnemyStatus())
            }
            MenuActionType.SKILL -> {
                val skillId = action.data ?: return
                val context = buildCombatContext(playerEntity, enemyEntity)
                val result = skillEngine.useSkill(skillId, context, sessionType = "combat")
                if (result.success) {
                    playerEntity.sendMessage("${result.message}")
                    playerEntity.sendMessage(formatEnemyStatus())
                } else {
                    playerEntity.sendMessage("技能失败：${result.message}")
                }
            }
            MenuActionType.DEFEND -> {
                playerEntity.sendMessage("你采取防御姿态。")
                // 防御效果由 Buff 或临时修正器实现（简化版直接跳过）
            }
            MenuActionType.FLEE -> {
                // 逃跑已在上层处理；此处作为兜底
                playerEntity.sendMessage("你试图逃跑……")
            }
            MenuActionType.DISABLED -> { /* 不应到达 */ }
        }
    }

    // ======================== 敌人回合 ========================

    private suspend fun enemyTurn(roundNumber: Int) {
        val state = CombatState(roundNumber, playerEntity, enemyEntity)
        val decision = enemyAI.decide(enemyEntity, enemyDefinition, state)

        when (decision.actionType) {
            AIActionType.SKILL -> {
                val skillId = decision.skillId
                if (skillId != null) {
                    val skill = skillEngine.getSkill(skillId)
                    val context = buildCombatContext(enemyEntity, playerEntity)
                    val result = skillEngine.useSkill(skillId, context, sessionType = "combat")
                    val skillName = skill?.displayName?.ifEmpty { skillId } ?: skillId
                    if (result.success) {
                        playerEntity.sendMessage("${enemyEntity.displayName} 使用了 $skillName！")
                    } else {
                        // 技能失败时执行普通攻击
                        val damage = calculateDamage(enemyEntity, playerEntity)
                        playerEntity.modifyAttribute("current_hp", -damage)
                        playerEntity.sendMessage("${enemyEntity.displayName} 发起攻击，造成 ${damage.toInt()} 点伤害！")
                    }
                }
            }
            AIActionType.DEFEND -> {
                playerEntity.sendMessage("${enemyEntity.displayName} 采取防御姿态。")
            }
            AIActionType.FLEE -> {
                playerEntity.sendMessage("${enemyEntity.displayName} 试图逃跑……但失败了。")
            }
        }

        playerEntity.sendMessage(formatPlayerStatus())
    }

    // ======================== 回合结束 ========================

    private suspend fun roundEndPhase() {
        // Buff tick
        val playerCtx = buildCombatContext(playerEntity, playerEntity)
        playerEntity.buffs.tick(playerCtx, effectEngine)

        val enemyCtx = buildCombatContext(enemyEntity, enemyEntity)
        enemyEntity.buffs.tick(enemyCtx, effectEngine)

        // CD tick
        playerEntity.cooldownManager.tickCooldowns(playerEntity.entityId)
        enemyEntity.cooldownManager.tickCooldowns(enemyEntity.entityId)
    }

    // ======================== 伤害计算 ========================

    /**
     * 使用全局伤害公式计算伤害
     *
     * 预处理 `attacker.` / `defender.` 前缀和 `random(N)` 后交给 FormulaEngine。
     */
    private fun calculateDamage(attacker: CombatEntity, defender: CombatEntity): Double {
        val ctx = CombatEffectContext(attacker, defender, emptyList(), emptyList(), combatConfig)

        // 基础伤害
        val formula = ctx.preprocessCombatFormula(combatConfig.damageFormula)
        var damage = ctx.resolveFormula(formula, attacker)

        // 暴击判定
        try {
            val critFormula = ctx.preprocessCombatFormula(combatConfig.critCheck)
            val critResult = ctx.resolveFormula(critFormula, attacker)
            if (critResult <= 0) { // critCheck 设计为 "random(100) - crit_rate"，<= 0 表示暴击
                damage *= combatConfig.critMultiplier
                // 暴击提示由调用方处理
            }
        } catch (_: Exception) { /* 暴击公式异常，跳过暴击 */ }

        // 最低伤害
        if (damage < combatConfig.minDamage) {
            damage = combatConfig.minDamage
        }

        return damage
    }

    // ======================== 胜负判定 ========================

    private fun checkWinLose(): CombatOutcome {
        if (!enemyEntity.isAlive) return CombatOutcome.PLAYER_WIN
        if (!playerEntity.isAlive) return CombatOutcome.PLAYER_LOSE
        return CombatOutcome.CONTINUE
    }

    // ======================== 战斗结算 ========================

    private suspend fun settlement() {
        val rewards = enemyDefinition.rewards ?: return

        // 经验结算
        if (rewards.expFormula != null) {
            try {
                val formula = rewards.expFormula
                    .replace("enemy.", "enemy_")
                    .replace("player.", "player_")
                val exp = FormulaEngine.evaluate(formula) { key ->
                    when {
                        key.startsWith("enemy_") -> enemyEntity.getAttributeValue(key.removePrefix("enemy_"))
                        key.startsWith("player_") -> playerEntity.getAttributeValue(key.removePrefix("player_"))
                        else -> enemyEntity.getAttributeValue(key)
                    }
                }
                playerEntity.modifyAttribute("exp", exp)
                playerEntity.sendMessage("获得 ${exp.toInt()} 经验值")
            } catch (e: Exception) {
                println("Warning: Exp formula evaluation failed: ${e.message}")
            }
        }

        // 掉落结算
        val obtainedItems = mutableListOf<String>()
        for (drop in rewards.drops) {
            if (Random.nextDouble() < drop.chance) {
                val qty = if (drop.quantityMin == drop.quantityMax) drop.quantityMin
                else Random.nextInt(drop.quantityMin, drop.quantityMax + 1)
                playerEntity.giveItem(drop.item, qty)
                obtainedItems.add("${drop.item} x$qty")
            }
        }
        if (obtainedItems.isNotEmpty()) {
            playerEntity.sendMessage("获得物品：${obtainedItems.joinToString("、")}")
        }
    }

    // ======================== 输入等待 ========================

    private suspend fun awaitPlayerInput(timeoutMs: Long): String {
        val deferred = CompletableDeferred<String>()
        inputDeferred = deferred
        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() } ?: "__timeout__"
        } finally {
            inputDeferred = null
        }
    }

    // ======================== 辅助方法 ========================

    private fun buildCombatContext(source: CombatEntity, target: CombatEntity): CombatEffectContext {
        val allies = if (source.isPlayer) listOf(playerEntity) else listOf(enemyEntity)
        val enemies = if (source.isPlayer) listOf(enemyEntity) else listOf(playerEntity)
        return CombatEffectContext(
            source = source,
            primaryTarget = target,
            allies = allies,
            enemies = enemies,
            combatConfig = combatConfig
        )
    }

    private fun formatEnemyStatus(): String {
        val hp = enemyEntity.getAttributeValue("current_hp").toInt()
        val maxHp = try { enemyEntity.getAttributeValue("max_hp").toInt() } catch (_: Exception) { hp }
        return "[${enemyEntity.displayName}] HP: $hp / $maxHp"
    }

    private fun formatPlayerStatus(): String {
        val hp = playerEntity.getAttributeValue("current_hp").toInt()
        val maxHp = try { playerEntity.getAttributeValue("max_hp").toInt() } catch (_: Exception) { hp }
        return "[你] HP: $hp / $maxHp"
    }
}

/**
 * 菜单动作类型
 */
enum class MenuActionType {
    ATTACK, SKILL, DEFEND, FLEE, DISABLED
}

/**
 * 菜单条目
 */
data class MenuEntry(
    val display: String,
    val actionType: MenuActionType,
    val data: String? = null
)

package org.textrpg.application.game.command

import java.util.concurrent.ConcurrentHashMap

/**
 * 会话接口
 *
 * 表示一个玩家进入的特殊交互模式（如战斗、商店、NPC 对话）。
 * 进入会话后，玩家的后续消息被该会话截获处理，不再走普通指令路由。
 *
 * 会话通过协程 suspend 实现等待玩家输入，不阻塞其他玩家。
 * 设计者可通过实现此接口定义自定义会话类型。
 */
interface Session {
    /** 会话类型标识（如 "combat"、"shop"、"dialogue"） */
    val type: String

    /** 绑定的玩家 ID */
    val playerId: String

    /** 会话是否仍然活跃 */
    fun isActive(): Boolean

    /**
     * 处理玩家在会话中发送的消息
     *
     * @param message 玩家发送的原始消息文本
     * @return 回复文本，null 表示无需回复
     */
    suspend fun handleMessage(message: String): String?

    /**
     * 会话超时时的处理
     *
     * 由 [SessionManager] 在超时检测时调用。
     * 实现类应在此方法中做清理工作（如战斗中自动防御）。
     */
    suspend fun onTimeout()
}

/**
 * 会话管理器
 *
 * 管理所有活跃会话的生命周期。线程安全（使用 [ConcurrentHashMap]）。
 *
 * 指令路由器在处理消息时先检查会话管理器：
 * 如果玩家处于活跃会话中，消息直接交由会话处理，不走指令路由。
 *
 * 使用示例：
 * ```kotlin
 * // 进入战斗会话
 * val battle = CombatSession(playerId, enemy)
 * sessionManager.startSession(battle)
 *
 * // 路由器自动将后续消息交给战斗会话
 * // 战斗结束后
 * sessionManager.endSession(playerId)
 * ```
 */
class SessionManager {

    /** 活跃会话映射（playerId -> Session） */
    private val activeSessions = ConcurrentHashMap<String, Session>()

    /**
     * 获取玩家当前活跃会话
     *
     * @param playerId 玩家 ID
     * @return 活跃会话实例，不在会话中返回 null
     */
    fun getSession(playerId: String): Session? {
        val session = activeSessions[playerId] ?: return null
        // 自动清理已失效的会话
        if (!session.isActive()) {
            activeSessions.remove(playerId)
            return null
        }
        return session
    }

    /**
     * 获取玩家当前会话类型
     *
     * @param playerId 玩家 ID
     * @return 会话类型字符串，不在会话中返回 null
     */
    fun getSessionType(playerId: String): String? = getSession(playerId)?.type

    /**
     * 启动新会话
     *
     * 如果玩家已有活跃会话，旧会话将被替换。
     *
     * @param session 新会话实例
     */
    fun startSession(session: Session) {
        activeSessions[session.playerId] = session
    }

    /**
     * 结束玩家的当前会话
     *
     * @param playerId 玩家 ID
     * @return 被结束的会话实例，没有活跃会话时返回 null
     */
    fun endSession(playerId: String): Session? {
        return activeSessions.remove(playerId)
    }

    /**
     * 检查玩家是否在会话中
     */
    fun isInSession(playerId: String): Boolean = getSession(playerId) != null
}

package org.textrpg.application.adapter.onebot

import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.*
import org.textrpg.application.data.config.AppConfig

/**
 * OneBot 适配器
 *
 * 统一外观类，管理 WebSocket 客户端和 HTTP API 客户端
 *
 * @property config OneBot 配置
 * @property httpClient Ktor HTTP 客户端（可选，默认创建新客户端）
 *
 * @example
 * ```kotlin
 * val adapter = OneBotAdapter(OneBotConfig(
 *     websocketUrl = "ws://127.0.0.1:8080",
 *     httpUrl = "http://127.0.0.1:8080"
 * ))
 *
 * adapter.registerMessageListener { event ->
 *     println("收到消息: ${event.getPlainText()}")
 * }
 *
 * adapter.connect()
 * adapter.sendPrivateMessage("123456", "你好！")
 * ```
 */
class OneBotAdapter(
    val config: AppConfig,
    private val httpClient: HttpClient? = null,
    logger: KLogger
) {
    private val client = httpClient ?: HttpClient()
    private val wsClient: WebSocketClient = WebSocketClient(config.bot, client, logger)
    private val httpApiClient: HttpApiClient = HttpApiClient(config.bot, client)

    /**
     * 是否已连接
     */
    val isConnected: Boolean
        get() = wsClient.isConnected

    /**
     * 连接 WebSocket
     *
     * @throws Exception 连接失败时抛出异常
     */
    suspend fun connect() {
        wsClient.connect()
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        wsClient.disconnect()
    }

    /**
     * 注册消息监听器
     *
     * 监听器由 [WebSocketClient] 持有；其内部已做异常隔离，
     * 单个 listener 抛错不会中断其他 listener 或 WebSocket 收消息循环。
     *
     * @param listener 消息监听回调
     * @return 监听器 ID，用于取消注册
     */
    fun registerMessageListener(listener: (MessageEvent) -> Unit): ListenerId {
        return wsClient.registerMessageListener(listener)
    }

    /**
     * 取消注册消息监听器
     *
     * @param id 监听器 ID
     * @return 是否成功取消
     */
    fun unregisterMessageListener(id: ListenerId): Boolean {
        return wsClient.unregisterMessageListener(id)
    }

    /**
     * 发送私聊消息（纯文本）
     *
     * @param userId 目标用户 ID
     * @param message 消息内容
     * @return 是否发送成功
     */
    suspend fun sendPrivateMessage(userId: String, message: String): Boolean {
        val result = httpApiClient.sendPrivateMessage(userId, message)
        return result.success
    }

    /**
     * 发送私聊消息（带消息段）
     *
     * @param userId 目标用户 ID
     * @param message 消息段列表
     * @return 是否发送成功
     */
    suspend fun sendPrivateMessage(userId: String, message: List<MessageSegment>): Boolean {
        val result = httpApiClient.sendPrivateMessage(userId, message)
        return result.success
    }

    /**
     * 发送群聊消息（纯文本）
     *
     * @param groupId 目标群 ID
     * @param message 消息内容
     * @return 是否发送成功
     */
    suspend fun sendGroupMessage(groupId: String, message: String): Boolean {
        val result = httpApiClient.sendGroupMessage(groupId, message)
        return result.success
    }

    /**
     * 发送群聊消息（带消息段）
     *
     * @param groupId 目标群 ID
     * @param message 消息段列表
     * @return 是否发送成功
     */
    suspend fun sendGroupMessage(groupId: String, message: List<MessageSegment>): Boolean {
        val result = httpApiClient.sendGroupMessage(groupId, message)
        return result.success
    }

    /**
     * 获取消息
     *
     * @param messageId 消息 ID
     * @return 消息信息，不存在时返回 null
     */
    suspend fun getMessage(messageId: String): MessageInfo? {
        return httpApiClient.getMessage(messageId)
    }

    /**
     * 删除消息
     *
     * @param messageId 消息 ID
     * @return 是否删除成功
     */
    suspend fun deleteMessage(messageId: String): Boolean {
        return httpApiClient.deleteMessage(messageId)
    }

    /**
     * 获取陌生人信息
     *
     * @param userId 用户 ID
     * @return 用户信息，不存在时返回 null
     */
    suspend fun getStrangerInfo(userId: String): UserInfo? {
        return httpApiClient.getStrangerInfo(userId)
    }

    /**
     * 获取群信息
     *
     * @param groupId 群 ID
     * @param noCache 是否不使用缓存
     * @return 群信息，不存在时返回 null
     */
    suspend fun getGroupInfo(groupId: String, noCache: Boolean = false): GroupInfo? {
        return httpApiClient.getGroupInfo(groupId, noCache)
    }

    /**
     * 获取群列表
     *
     * @return 群信息列表
     */
    suspend fun getGroupList(): List<GroupInfo> {
        return httpApiClient.getGroupList()
    }

    /**
     * 获取群成员信息
     *
     * @param groupId 群 ID
     * @param userId 用户 ID
     * @param noCache 是否不使用缓存
     * @return 群成员信息，不存在时返回 null
     */
    suspend fun getGroupMemberInfo(groupId: String, userId: String, noCache: Boolean = false): GroupMemberInfo? {
        return httpApiClient.getGroupMemberInfo(groupId, userId, noCache)
    }

    /**
     * 获取群成员列表
     *
     * @param groupId 群 ID
     * @return 群成员信息列表
     */
    suspend fun getGroupMemberList(groupId: String): List<GroupMemberInfo> {
        return httpApiClient.getGroupMemberList(groupId)
    }

    /**
     * 关闭适配器，释放资源
     *
     * 仅释放本适配器**自有**的资源：
     * - 断开 WebSocket 连接（[wsClient]）
     * - 若 [httpClient] 是构造时未注入而由本类自己 new 出来的，则一并 close
     *
     * 注入的 [httpClient] 由调用方管理生命周期，不在此处关闭——
     * 否则会破坏其他共用同一 client 的组件（如 [HttpApiClient] / [WebSocketClient]）。
     */
    fun close() {
        disconnect()
        if (httpClient == null) {
            client.close()
        }
    }
}

package org.textrpg.application.adapter.onebot

import io.ktor.client.*
import kotlinx.coroutines.*

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
    val config: OneBotConfig,
    private val httpClient: HttpClient? = null
) {
    private val mClient = httpClient ?: HttpClient()
    private val mScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val mWsClient: WebSocketClient = WebSocketClient(config, mClient)
    private val mHttpApiClient: HttpApiClient = HttpApiClient(config)

    private val mListeners = mutableListOf<ListenerId>()

    init {
        // 注册内部消息监听器，转发给外部监听器
        mWsClient.registerMessageListener { event ->
            mScope.launch {
                mMessageListeners.forEach { (_, listener) ->
                    try {
                        listener(event)
                    } catch (e: Exception) {
                        println("Error in message listener: ${e.message}")
                    }
                }
            }
        }
    }

    private val mMessageListeners = mutableMapOf<ListenerId, (MessageEvent) -> Unit>()

    /**
     * 是否已连接
     */
    val isConnected: Boolean
        get() = mWsClient.isConnected

    /**
     * 连接 WebSocket
     *
     * @throws Exception 连接失败时抛出异常
     */
    suspend fun connect() {
        mWsClient.connect()
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        mWsClient.disconnect()
        mScope.cancel()
    }

    /**
     * 注册消息监听器
     *
     * @param listener 消息监听回调
     * @return 监听器 ID，用于取消注册
     */
    fun registerMessageListener(listener: (MessageEvent) -> Unit): ListenerId {
        val id = mWsClient.registerMessageListener(listener)
        mListeners.add(id)
        return id
    }

    /**
     * 取消注册消息监听器
     *
     * @param id 监听器 ID
     * @return 是否成功取消
     */
    fun unregisterMessageListener(id: ListenerId): Boolean {
        return mWsClient.unregisterMessageListener(id)
    }

    /**
     * 发送私聊消息（纯文本）
     *
     * @param userId 目标用户 ID
     * @param message 消息内容
     * @return 是否发送成功
     */
    suspend fun sendPrivateMessage(userId: String, message: String): Boolean {
        val result = mHttpApiClient.sendPrivateMessage(userId, message)
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
        val result = mHttpApiClient.sendPrivateMessage(userId, message)
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
        val result = mHttpApiClient.sendGroupMessage(groupId, message)
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
        val result = mHttpApiClient.sendGroupMessage(groupId, message)
        return result.success
    }

    /**
     * 获取消息
     *
     * @param messageId 消息 ID
     * @return 消息信息，不存在时返回 null
     */
    suspend fun getMessage(messageId: String): MessageInfo? {
        return mHttpApiClient.getMessage(messageId)
    }

    /**
     * 删除消息
     *
     * @param messageId 消息 ID
     * @return 是否删除成功
     */
    suspend fun deleteMessage(messageId: String): Boolean {
        return mHttpApiClient.deleteMessage(messageId)
    }

    /**
     * 获取陌生人信息
     *
     * @param userId 用户 ID
     * @return 用户信息，不存在时返回 null
     */
    suspend fun getStrangerInfo(userId: String): UserInfo? {
        return mHttpApiClient.getStrangerInfo(userId)
    }

    /**
     * 获取群信息
     *
     * @param groupId 群 ID
     * @param noCache 是否不使用缓存
     * @return 群信息，不存在时返回 null
     */
    suspend fun getGroupInfo(groupId: String, noCache: Boolean = false): GroupInfo? {
        return mHttpApiClient.getGroupInfo(groupId, noCache)
    }

    /**
     * 获取群列表
     *
     * @return 群信息列表
     */
    suspend fun getGroupList(): List<GroupInfo> {
        return mHttpApiClient.getGroupList()
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
        return mHttpApiClient.getGroupMemberInfo(groupId, userId, noCache)
    }

    /**
     * 获取群成员列表
     *
     * @param groupId 群 ID
     * @return 群成员信息列表
     */
    suspend fun getGroupMemberList(groupId: String): List<GroupMemberInfo> {
        return mHttpApiClient.getGroupMemberList(groupId)
    }

    /**
     * 关闭适配器，释放资源
     */
    fun close() {
        disconnect()
        mHttpApiClient.close()
        if (httpClient == null) {
            mClient.close()
        }
    }
}

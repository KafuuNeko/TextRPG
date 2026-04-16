package org.textrpg.application.adapter.onebot

import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.textrpg.application.data.config.OneBotConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * WebSocket 事件客户端
 *
 * 通过 WebSocket 接收 OneBot 事件推送
 *
 * @property config OneBot 配置
 * @property httpClient Ktor HTTP 客户端
 * @property logger 日志记录器
 */
class WebSocketClient(
    private val config: OneBotConfig,
    private val httpClient: HttpClient,
    private val logger: KLogger
) {
    private var scopeJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.IO + scopeJob)
    private var session: DefaultClientWebSocketSession? = null
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val echoCounter = AtomicLong(1)
    private val listenerIdCounter = AtomicLong(1)
    private val listeners = ConcurrentHashMap<ListenerId, (MessageEvent) -> Unit>()
    @Volatile
    private var isConnecting = false
    @Volatile
    private var shouldReconnect = true

    /**
     * 是否已连接
     */
    @Volatile
    var isConnected: Boolean = false
        private set

    /**
     * 连接 WebSocket
     */
    suspend fun connect() {
        if (isConnecting || isConnected) return
        isConnecting = true
        shouldReconnect = true

        try {
            httpClient.webSocket(urlString = config.websocketUrl) {
                session = this
                isConnected = true
                isConnecting = false
                logger.info { "WebSocket connected to ${config.websocketUrl}" }

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> handleMessage(frame.readText())
                        is Frame.Close -> {
                            isConnected = false
                        }
                        else -> {}
                    }
                }
            }
        } catch (e: Exception) {
            isConnected = false
            isConnecting = false
            if (shouldReconnect) {
                scope.launch {
                    delay(config.reconnectInterval)
                    connect()
                }
            }
            throw e
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        shouldReconnect = false
        scopeJob.cancel()                // 仅取消子 Job，不毁掉 scope 本身
        session?.cancel()
        session = null
        isConnected = false
        isConnecting = false
        // 重建 scope 以便后续可能的 reconnect
        scopeJob = SupervisorJob()
        scope = CoroutineScope(Dispatchers.IO + scopeJob)
    }

    /**
     * 注册消息监听器
     *
     * @param listener 消息监听回调
     * @return 监听器 ID，用于取消注册
     */
    fun registerMessageListener(listener: (MessageEvent) -> Unit): ListenerId {
        val id = ListenerId(listenerIdCounter.getAndIncrement())
        listeners[id] = listener
        return id
    }

    /**
     * 取消注册消息监听器
     *
     * @param id 监听器 ID
     * @return 是否成功取消
     */
    fun unregisterMessageListener(id: ListenerId): Boolean {
        return listeners.remove(id) != null
    }

    /**
     * 处理收到的 WebSocket 消息
     */
    private suspend fun handleMessage(text: String) {
        runCatching {
            val json = Json.parseToJsonElement(text).jsonObject
            val postType = json["post_type"]?.jsonPrimitive?.contentOrNull

            when (postType) {
                "message" -> handleMessageEvent(json)
                "meta_event" -> handleMetaEvent(json)
                ".respond" -> handleApiResponse(json)
            }
        }.onFailure { e ->
            logger.warn(e) { "Error handling WebSocket message" }
        }
    }

    /**
     * 处理消息事件
     */
    private fun handleMessageEvent(json: JsonObject) {
        val messageType = json["message_type"]?.jsonPrimitive?.contentOrNull ?: return
        val userId = json["user_id"]?.jsonPrimitive?.contentOrNull ?: return
        val rawMessage = json["message"]
        val messageSegments = parseMessage(rawMessage)

        val event = MessageEvent(
            postType = json["post_type"]?.jsonPrimitive?.contentOrNull ?: "",
            messageType = messageType,
            subType = json["sub_type"]?.jsonPrimitive?.contentOrNull ?: "",
            userId = userId,
            groupId = json["group_id"]?.jsonPrimitive?.contentOrNull?.takeIf { messageType == "group" },
            message = messageSegments,
            rawEvent = json
        )

        // 异常隔离：单个 listener 抛错不应阻断后续 listener 或 WebSocket 收消息循环
        listeners.values.forEach { listener ->
            runCatching { listener.invoke(event) }
                .onFailure { logger.warn(it) { "Message listener threw" } }
        }
    }

    /**
     * 处理元事件
     */
    private fun handleMetaEvent(json: JsonObject) {
        val metaEventType = json["meta_event_type"]?.jsonPrimitive?.contentOrNull
        when (metaEventType) {
            "heartbeat" -> {} // 心跳事件，忽略
            "lifecycle" -> {}  // 生命周期事件
        }
    }

    /**
     * 处理 API 响应
     */
    private fun handleApiResponse(json: JsonObject) {
        val echo = json["echo"]?.jsonPrimitive?.contentOrNull ?: return
        pendingRequests.remove(echo)?.complete(json)
    }

    /**
     * 解析消息
     */
    private fun parseMessage(messageElement: JsonElement?): List<MessageSegment> {
        if (messageElement == null) return emptyList()

        return when (messageElement) {
            is JsonArray -> messageElement.mapNotNull { parseMessageSegment(it.jsonObject) }
            is JsonPrimitive -> listOf(MessageSegment.text(messageElement.content))
            else -> emptyList()
        }
    }

    /**
     * 解析消息段
     */
    private fun parseMessageSegment(obj: JsonObject): MessageSegment? {
        val typeStr = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val data = obj["data"]?.jsonObject ?: return MessageSegment(MessageSegmentType.CUSTOM, emptyMap())
        val dataMap = data.entries.associate { (key, value) ->
            key to when (value) {
                is JsonPrimitive -> value.contentOrNull ?: ""
                else -> value.toString()   // 嵌套对象/数组退化为 JSON 字符串
            }
        }

        val type = runCatching { MessageSegmentType.valueOf(typeStr.uppercase()) }
            .getOrDefault(MessageSegmentType.CUSTOM)

        return MessageSegment(type, dataMap)
    }

    /**
     * 发送 WebSocket 请求
     */
    suspend fun sendRequest(action: String, params: JsonObject): JsonObject? {
        val echo = echoCounter.getAndIncrement().toString()
        val deferred = CompletableDeferred<JsonObject>()
        pendingRequests[echo] = deferred

        val payload = buildJsonObject {
            put("action", action)
            put("echo", echo)
            put("params", params)
        }

        session?.send(Frame.Text(payload.toString()))

        return runCatching { withTimeout(30000) { deferred.await() } }
            .getOrElse {
                pendingRequests.remove(echo)
                null
            }
    }
}

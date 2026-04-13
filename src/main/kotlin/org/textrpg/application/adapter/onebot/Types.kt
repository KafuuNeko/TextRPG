package org.textrpg.application.adapter.onebot

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * OneBot 消息段类型枚举
 */
enum class MessageSegmentType {
    TEXT,
    IMAGE,
    AT,
    FACE,
    RECORD,
    VIDEO,
    REPLY,
    FORWARD,
    POKE,
    SHARE,
    LOCATION,
    MUSIC,
    CUSTOM
}

/**
 * OneBot 消息段
 *
 * @property type 消息段类型
 * @property data 消息段数据
 */
@Serializable
data class MessageSegment(
    val type: MessageSegmentType,
    val data: Map<String, String> = emptyMap()
) {
    companion object {
        fun text(content: String) = MessageSegment(MessageSegmentType.TEXT, mapOf("text" to content))

        fun image(file: String, url: String? = null) = MessageSegment(
            MessageSegmentType.IMAGE,
            buildMap {
                put("file", file)
                url?.let { put("url", it) }
            }
        )

        fun at(userId: String) = MessageSegment(MessageSegmentType.AT, mapOf("qq" to userId))

        fun face(id: String) = MessageSegment(MessageSegmentType.FACE, mapOf("id" to id))

        fun reply(id: String) = MessageSegment(MessageSegmentType.REPLY, mapOf("id" to id))

        fun record(file: String) = MessageSegment(MessageSegmentType.RECORD, mapOf("file" to file))

        fun video(file: String) = MessageSegment(MessageSegmentType.VIDEO, mapOf("file" to file))

        fun forward(id: String) = MessageSegment(MessageSegmentType.FORWARD, mapOf("id" to id))
    }

    fun toCQCode(): String {
        return when (type) {
            MessageSegmentType.TEXT -> data["text"] ?: ""
            MessageSegmentType.IMAGE -> buildCQCode("image", data)
            MessageSegmentType.AT -> buildCQCode("at", data)
            MessageSegmentType.FACE -> buildCQCode("face", data)
            MessageSegmentType.RECORD -> buildCQCode("record", data)
            MessageSegmentType.VIDEO -> buildCQCode("video", data)
            MessageSegmentType.REPLY -> buildCQCode("reply", data)
            MessageSegmentType.FORWARD -> buildCQCode("forward", data)
            MessageSegmentType.POKE -> buildCQCode("poke", data)
            MessageSegmentType.SHARE -> buildCQCode("share", data)
            MessageSegmentType.LOCATION -> buildCQCode("location", data)
            MessageSegmentType.MUSIC -> buildCQCode("music", data)
            MessageSegmentType.CUSTOM -> buildCQCode("custom", data)
        }
    }

    private fun buildCQCode(type: String, data: Map<String, String>): String {
        if (data.isEmpty()) return ""
        val params = data.entries.joinToString(",") { "${it.key}=${it.value}" }
        return "[CQ:$type,$params]"
    }
}

/**
 * 消息事件
 *
 * @property postType 事件类型 (message, notice, request, meta_event)
 * @property messageType 消息类型 (private, group)
 * @property subType 子类型
 * @property userId 发送者用户 ID
 * @property groupId 群 ID（私聊时为 null）
 * @property message 消息段列表
 * @property rawEvent 原始事件数据
 */
data class MessageEvent(
    val postType: String,
    val messageType: String,
    val subType: String = "",
    val userId: String,
    val groupId: String? = null,
    val message: List<MessageSegment>,
    val rawEvent: JsonObject
) {
    val isPrivate: Boolean get() = messageType == "private"
    val isGroup: Boolean get() = messageType == "group"

    fun getPlainText(): String {
        return message.filter { it.type == MessageSegmentType.TEXT }.joinToString("") { it.data["text"] ?: "" }
    }

    fun toCQString(): String {
        return message.joinToString("") { it.toCQCode() }
    }
}

/**
 * API 响应
 *
 * @property success 是否成功
 * @property data 响应数据
 * @property code 返回码
 * @property msg 返回信息
 */
data class ApiResponse(
    val success: Boolean,
    val data: JsonElement? = null,
    val code: Int,
    val msg: String
)

/**
 * 用户信息
 *
 * @property userId 用户 ID
 * @property nickname 昵称
 * @property sex 性别 (male/female/unknown)
 * @property age 年龄
 */
@Serializable
data class UserInfo(
    val userId: String,
    val nickname: String,
    val sex: String = "unknown",
    val age: Int = 0
)

/**
 * 群信息
 *
 * @property groupId 群 ID
 * @property groupName 群名称
 * @property memberCount 成员数量
 * @property maxMemberCount 最大成员数量
 */
@Serializable
data class GroupInfo(
    val groupId: String,
    val groupName: String,
    val memberCount: Int = 0,
    val maxMemberCount: Int = 0
)

/**
 * 群成员信息
 *
 * @property groupId 群 ID
 * @property userId 用户 ID
 * @property nickname 昵称
 * @property card 群名片
 * @property sex 性别
 * @property age 年龄
 * @property role 角色 (owner/admin/member)
 * @property joinTime 加入时间
 */
@Serializable
data class GroupMemberInfo(
    val groupId: String,
    val userId: String,
    val nickname: String,
    val card: String = "",
    val sex: String = "unknown",
    val age: Int = 0,
    val role: String = "member",
    val joinTime: Int = 0
)

/**
 * 消息信息
 *
 * @property messageId 消息 ID
 * @property realId 真实消息 ID
 * @property message 消息内容
 * @property sender 发送者信息
 */
@Serializable
data class MessageInfo(
    val messageId: Int,
    val realId: Int,
    val message: String,
    val sender: MessageSender
)

/**
 * 消息发送者信息
 */
@Serializable
data class MessageSender(
    val userId: String,
    val nickname: String,
    val sex: String = "unknown",
    val age: Int = 0,
    val groupId: Long = 0,
    val card: String = ""
)

/**
 * OneBot 配置
 *
 * @property websocketUrl WebSocket 连接地址
 * @property httpUrl HTTP API 地址
 * @property accessToken 访问令牌（可选）
 * @property reconnectInterval 重连间隔（毫秒）
 */
data class OneBotConfig(
    val websocketUrl: String = "ws://127.0.0.1:8080",
    val httpUrl: String = "http://127.0.0.1:8080",
    val accessToken: String? = null,
    val reconnectInterval: Long = 5000L
)

/**
 * 监听器 ID，用于取消注册
 */
@JvmInline
value class ListenerId(val id: Long)

/**
 * 事件监听器
 */
interface EventListener {
    fun onMessage(event: MessageEvent) {}
    fun onConnect() {}
    fun onDisconnect() {}
    fun onError(e: Throwable) {}
}

/**
 * 消息类型
 */
object Message {
    fun text(content: String): List<MessageSegment> = listOf(MessageSegment.text(content))

    fun image(file: String, url: String? = null): List<MessageSegment> = listOf(MessageSegment.image(file, url))

    fun at(userId: String): List<MessageSegment> = listOf(MessageSegment.at(userId))

    fun compose(vararg segments: MessageSegment): List<MessageSegment> = segments.toList()

    fun fromSegments(segments: List<MessageSegment>): List<MessageSegment> = segments

    fun of(segment: MessageSegment): List<MessageSegment> = listOf(segment)
}

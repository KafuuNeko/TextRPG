package org.textrpg.application.adapter.onebot

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * HTTP API 响应
 */
data class HttpApiResult(
    val success: Boolean,
    val data: JsonElement?,
    val retcode: Int,
    val errmsg: String
)

/**
 * HTTP API 客户端
 *
 * 用于调用 OneBot HTTP API
 *
 * @property mConfig OneBot 配置
 */
class HttpApiClient(private val mConfig: OneBotConfig) {
    private val mClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 10000
        }
    }

    private val mJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val mMutex = Mutex()
    private var mEchoCounter = 0L

    /**
     * 获取新的 echo 值
     */
    private suspend fun nextEcho(): String {
        mMutex.withLock {
            mEchoCounter++
            return "http_${System.currentTimeMillis()}_$mEchoCounter"
        }
    }

    /**
     * 发送私聊消息
     *
     * @param userId 目标用户 ID
     * @param message 消息内容
     * @param autoEscape 是否转义 CQ 码
     * @return API 结果
     */
    suspend fun sendPrivateMessage(userId: String, message: String, autoEscape: Boolean = false): HttpApiResult {
        return callApi("send_private_msg", buildJsonObject {
            put("user_id", JsonPrimitive(userId))
            put("message", JsonPrimitive(message))
            put("auto_escape", JsonPrimitive(autoEscape))
        })
    }

    /**
     * 发送群聊消息
     *
     * @param groupId 目标群 ID
     * @param message 消息内容
     * @param autoEscape 是否转义 CQ 码
     * @return API 结果
     */
    suspend fun sendGroupMessage(groupId: String, message: String, autoEscape: Boolean = false): HttpApiResult {
        return callApi("send_group_msg", buildJsonObject {
            put("group_id", JsonPrimitive(groupId))
            put("message", JsonPrimitive(message))
            put("auto_escape", JsonPrimitive(autoEscape))
        })
    }

    /**
     * 发送私聊消息（带消息段）
     *
     * @param userId 目标用户 ID
     * @param message 消息段列表
     * @param autoEscape 是否转义 CQ 码
     * @return API 结果
     */
    suspend fun sendPrivateMessage(userId: String, message: List<MessageSegment>, autoEscape: Boolean = false): HttpApiResult {
        val messageStr = message.joinToString("") { it.toCQCode() }
        return sendPrivateMessage(userId, messageStr, autoEscape)
    }

    /**
     * 发送群聊消息（带消息段）
     *
     * @param groupId 目标群 ID
     * @param message 消息段列表
     * @param autoEscape 是否转义 CQ 码
     * @return API 结果
     */
    suspend fun sendGroupMessage(groupId: String, message: List<MessageSegment>, autoEscape: Boolean = false): HttpApiResult {
        val messageStr = message.joinToString("") { it.toCQCode() }
        return sendGroupMessage(groupId, messageStr, autoEscape)
    }

    /**
     * 获取消息
     *
     * @param messageId 消息 ID
     * @return 消息信息
     */
    suspend fun getMessage(messageId: String): MessageInfo? {
        val result = callApi("get_msg", buildJsonObject { put("message_id", JsonPrimitive(messageId)) })
        return if (result.success && result.data != null) {
            try {
                mJson.decodeFromString<MessageInfo>(result.data.toString())
            } catch (e: Exception) {
                null
            }
        } else null
    }

    /**
     * 撤回消息
     *
     * @param messageId 消息 ID
     * @return 是否成功
     */
    suspend fun deleteMessage(messageId: String): Boolean {
        val result = callApi("delete_msg", buildJsonObject { put("message_id", JsonPrimitive(messageId)) })
        return result.success
    }

    /**
     * 获取陌生人信息
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    suspend fun getStrangerInfo(userId: String): UserInfo? {
        val result = callApi("get_stranger_info", buildJsonObject { put("user_id", JsonPrimitive(userId)) })
        return if (result.success && result.data != null) {
            try {
                mJson.decodeFromString<UserInfo>(result.data.toString())
            } catch (e: Exception) {
                null
            }
        } else null
    }

    /**
     * 获取群信息
     *
     * @param groupId 群 ID
     * @param noCache 是否不使用缓存
     * @return 群信息
     */
    suspend fun getGroupInfo(groupId: String, noCache: Boolean = false): GroupInfo? {
        val result = callApi("get_group_info", buildJsonObject {
            put("group_id", JsonPrimitive(groupId))
            put("no_cache", JsonPrimitive(noCache))
        })
        return if (result.success && result.data != null) {
            try {
                mJson.decodeFromString<GroupInfo>(result.data.toString())
            } catch (e: Exception) {
                null
            }
        } else null
    }

    /**
     * 获取群列表
     *
     * @return 群信息列表
     */
    suspend fun getGroupList(): List<GroupInfo> {
        val result = callApi("get_group_list", buildJsonObject { })
        return if (result.success && result.data != null) {
            try {
                mJson.decodeFromString<List<GroupInfo>>(result.data.toString())
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()
    }

    /**
     * 获取群成员信息
     *
     * @param groupId 群 ID
     * @param userId 用户 ID
     * @param noCache 是否不使用缓存
     * @return 群成员信息
     */
    suspend fun getGroupMemberInfo(groupId: String, userId: String, noCache: Boolean = false): GroupMemberInfo? {
        val result = callApi("get_group_member_info", buildJsonObject {
            put("group_id", JsonPrimitive(groupId))
            put("user_id", JsonPrimitive(userId))
            put("no_cache", JsonPrimitive(noCache))
        })
        return if (result.success && result.data != null) {
            try {
                mJson.decodeFromString<GroupMemberInfo>(result.data.toString())
            } catch (e: Exception) {
                null
            }
        } else null
    }

    /**
     * 获取群成员列表
     *
     * @param groupId 群 ID
     * @return 群成员信息列表
     */
    suspend fun getGroupMemberList(groupId: String): List<GroupMemberInfo> {
        val result = callApi("get_group_member_list", buildJsonObject { put("group_id", JsonPrimitive(groupId)) })
        return if (result.success && result.data != null) {
            try {
                mJson.decodeFromString<List<GroupMemberInfo>>(result.data.toString())
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()
    }

    /**
     * 调用 OneBot API
     *
     * @param action API 动作名
     * @param params API 参数
     * @return API 结果
     */
    private suspend fun callApi(action: String, params: JsonElement): HttpApiResult {
        return try {
            val requestBody = buildJsonObject {
                put("action", JsonPrimitive(action))
                put("params", params)
            }

            val response = mClient.post("${mConfig.httpUrl}/api") {
                contentType(ContentType.Application.Json)
                header("Authorization", mConfig.accessToken?.let { "Bearer $it" })
                setBody(requestBody.toString())
            }

            if (response.status.isSuccess()) {
                val body = response.body<String>()
                val jsonResponse = mJson.parseToJsonElement(body).jsonObject
                val retcode = jsonResponse["retcode"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                HttpApiResult(
                    success = retcode == 0,
                    data = jsonResponse["data"],
                    retcode = retcode,
                    errmsg = jsonResponse["errmsg"]?.jsonPrimitive?.content ?: ""
                )
            } else {
                HttpApiResult(
                    success = false,
                    data = null,
                    retcode = response.status.value,
                    errmsg = response.status.description
                )
            }
        } catch (e: Exception) {
            HttpApiResult(
                success = false,
                data = null,
                retcode = -1,
                errmsg = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 关闭客户端
     */
    fun close() {
        mClient.close()
    }
}

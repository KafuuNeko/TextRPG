package org.textrpg.application.adapter.onebot

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
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
 * 用于调用 OneBot HTTP API。
 *
 * **超时**：每次请求通过 [HttpTimeout] 插件按调用配置（30s 请求 / 10s 连接），
 * 与注入的 [HttpClient] 全局配置无关，避免对调用方 client 的隐性依赖。
 *
 * @param config OneBot 配置
 * @param client Ktor HTTP 客户端（必须已安装 [HttpTimeout] 插件，由 Application 层统一注入）
 */
class HttpApiClient(
    private val config: OneBotConfig,
    private val client: HttpClient
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
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
    suspend fun getMessage(messageId: String): MessageInfo? =
        callApi("get_msg", buildJsonObject { put("message_id", JsonPrimitive(messageId)) })
            .decodeData()

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
    suspend fun getStrangerInfo(userId: String): UserInfo? =
        callApi("get_stranger_info", buildJsonObject { put("user_id", JsonPrimitive(userId)) })
            .decodeData()

    /**
     * 获取群信息
     *
     * @param groupId 群 ID
     * @param noCache 是否不使用缓存
     * @return 群信息
     */
    suspend fun getGroupInfo(groupId: String, noCache: Boolean = false): GroupInfo? =
        callApi("get_group_info", buildJsonObject {
            put("group_id", JsonPrimitive(groupId))
            put("no_cache", JsonPrimitive(noCache))
        }).decodeData()

    /**
     * 获取群列表
     *
     * @return 群信息列表
     */
    suspend fun getGroupList(): List<GroupInfo> =
        callApi("get_group_list", buildJsonObject { })
            .decodeData<List<GroupInfo>>() ?: emptyList()

    /**
     * 获取群成员信息
     *
     * @param groupId 群 ID
     * @param userId 用户 ID
     * @param noCache 是否不使用缓存
     * @return 群成员信息
     */
    suspend fun getGroupMemberInfo(groupId: String, userId: String, noCache: Boolean = false): GroupMemberInfo? =
        callApi("get_group_member_info", buildJsonObject {
            put("group_id", JsonPrimitive(groupId))
            put("user_id", JsonPrimitive(userId))
            put("no_cache", JsonPrimitive(noCache))
        }).decodeData()

    /**
     * 获取群成员列表
     *
     * @param groupId 群 ID
     * @return 群成员信息列表
     */
    suspend fun getGroupMemberList(groupId: String): List<GroupMemberInfo> =
        callApi("get_group_member_list", buildJsonObject { put("group_id", JsonPrimitive(groupId)) })
            .decodeData<List<GroupMemberInfo>>() ?: emptyList()

    /**
     * 把 [HttpApiResult.data] 解析为指定类型
     *
     * 失败/无数据时返回 null。统一所有 get 方法的解码逻辑，避免重复 try-catch。
     */
    private inline fun <reified T> HttpApiResult.decodeData(): T? {
        if (!success || data == null) return null
        return runCatching { json.decodeFromString<T>(data.toString()) }.getOrNull()
    }

    /**
     * 调用 OneBot API
     *
     * @param action API 动作名
     * @param params API 参数
     * @return API 结果
     */
    private suspend fun callApi(action: String, params: JsonElement): HttpApiResult {
        return runCatching {
            val requestBody = buildJsonObject {
                put("action", JsonPrimitive(action))
                put("params", params)
            }

            val response = client.post("${config.httpUrl}/api") {
                contentType(ContentType.Application.Json)
                config.accessToken?.takeIf { it.isNotBlank() }?.let {
                    header("Authorization", "Bearer $it")
                }
                setBody(requestBody.toString())
            }

            if (response.status.isSuccess()) {
                val body = response.body<String>()
                val jsonResponse = json.parseToJsonElement(body).jsonObject
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
        }.getOrElse { e ->
            HttpApiResult(
                success = false,
                data = null,
                retcode = -1,
                errmsg = e.message ?: "Unknown error"
            )
        }
    }

}

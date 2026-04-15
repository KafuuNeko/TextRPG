package org.textrpg.application.data.config

import org.textrpg.application.domain.model.*

data class QuestConfig(val quests: Map<String, QuestDefinition> = emptyMap())

/**
 * 任务配置加载器
 *
 * 从 YAML 文件加载任务定义。
 */
object QuestConfigLoader : AbstractYamlLoader<QuestConfig>() {
    override val defaultPath = "src/main/resources/config/quests.yaml"
    override val default = QuestConfig()
    override val configName = "Quest"

    override fun parse(raw: Map<String, Any>): QuestConfig {
        @Suppress("UNCHECKED_CAST")
        val questsRaw = raw["quests"] as? Map<String, Map<String, Any>> ?: return QuestConfig()
        return QuestConfig(quests = questsRaw.mapValues { (key, props) -> parseQuest(key, props) })
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseQuest(key: String, props: Map<String, Any>): QuestDefinition {
        return QuestDefinition(
            key = key,
            displayName = props["displayName"] as? String ?: key,
            description = props["description"] as? String ?: "",
            giver = props["giver"] as? String ?: "",
            objectives = parseObjectives(props["objectives"]),
            rewards = parseRewards(props["rewards"]),
            requires = RequiresParser.parseRequires(props["requires"]),
            unlocks = parseUnlocks(props["unlocks"])
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseObjectives(raw: Any?): List<QuestObjective> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val props = item as? Map<String, Any> ?: return@mapNotNull null
            val typeStr = props["type"] as? String ?: return@mapNotNull null
            QuestObjective(
                type = ObjectiveType.fromValue(typeStr),
                target = (props["item"] ?: props["enemy"] ?: props["node"] ?: props["npc"] ?: props["target"])
                    ?.toString() ?: "",
                quantity = (props["quantity"] as? Number)?.toInt() ?: 1,
                scriptPath = props["scriptPath"] as? String
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRewards(raw: Any?): QuestRewards {
        val map = raw as? Map<String, Any> ?: return QuestRewards()
        return QuestRewards(
            exp = RequiresParser.toDouble(map["exp"]) ?: 0.0,
            items = (map["items"] as? List<*>)?.mapNotNull { item ->
                val props = item as? Map<String, Any> ?: return@mapNotNull null
                val itemId = props["item"] as? String ?: return@mapNotNull null
                QuestRewardItem(
                    item = itemId,
                    quantity = (props["quantity"] as? Number)?.toInt() ?: 1
                )
            } ?: emptyList(),
            attributeChanges = (map["attributeChanges"] as? Map<String, Any>
                ?: map["attributeChange"] as? Map<String, Any>)
                ?.mapValues { (_, v) -> RequiresParser.toDouble(v) ?: 0.0 }
                ?: emptyMap()
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseUnlocks(raw: Any?): QuestUnlocks {
        val map = raw as? Map<String, Any> ?: return QuestUnlocks()
        return QuestUnlocks(
            quests = (map["quests"] as? List<*>)?.map { it.toString() } ?: emptyList()
        )
    }
}

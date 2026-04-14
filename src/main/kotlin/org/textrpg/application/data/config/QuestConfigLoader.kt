package org.textrpg.application.data.config

import org.textrpg.application.domain.model.*
import org.yaml.snakeyaml.Yaml
import java.io.File

data class QuestConfig(val quests: Map<String, QuestDefinition> = emptyMap())

/**
 * 任务配置加载器
 *
 * 从 YAML 文件加载任务定义。
 */
object QuestConfigLoader {
    private val yaml = Yaml()
    private const val DEFAULT_PATH = "src/main/resources/config/quests.yaml"

    fun load(path: String = DEFAULT_PATH): QuestConfig {
        val file = File(path)
        if (!file.exists()) {
            println("Warning: Quest config not found at $path, using empty definitions")
            return QuestConfig()
        }
        return try {
            val raw = yaml.load<Map<String, Any>>(file.readText()) ?: return QuestConfig()
            @Suppress("UNCHECKED_CAST")
            val questsRaw = raw["quests"] as? Map<String, Map<String, Any>> ?: emptyMap()
            val quests = questsRaw.mapValues { (key, props) -> parseQuest(key, props) }
            QuestConfig(quests = quests)
        } catch (e: Exception) {
            println("Warning: Failed to load quest config from $path: ${e.message}")
            QuestConfig()
        }
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
            attributeChanges = (map["attributeChange"] as? Map<String, Any>)
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

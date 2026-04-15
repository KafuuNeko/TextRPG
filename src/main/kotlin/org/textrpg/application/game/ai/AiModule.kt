package org.textrpg.application.game.ai

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.textrpg.application.adapter.llm.FunctionCallingClient
import org.textrpg.application.adapter.llm.LLMClient
import org.textrpg.application.adapter.llm.OpenAIFunctionCallingClient

/**
 * AI 代理基础设施 Koin 模块（Step 7 接入）
 *
 * 注册完整的 AI Agent 基础设施，使 `/对话` / `/探索` / Boss 决策等场景
 * 可通过 [AISceneManager] 统一调度。
 *
 * **模块单例**：[FunctionCallingClient]（绑定 [OpenAIFunctionCallingClient]）/
 * [PlayerTagManager] / [AIToolRegistry]（init 时注册 [BuiltinAITools] 5 个工具）/
 * [AISceneManager]。
 *
 * **依赖**：`appModule`（[LLMClient] / `AppConfig` / `HttpClient`）、
 * `configModule`（`AIConfig`）、`gameModule`（`MapManager` / `NpcManager` /
 * `QuestManager` 给 [BuiltinAITools] 用）。
 *
 * **启动顺序**：`aiModule` 必须在 `gameModule` / `configModule` / `appModule` 之后。
 */
val aiModule = module {
    // 复用 appModule 的 LLMClient 单例作为 delegate，避免重复创建 OpenAI 客户端
    single<FunctionCallingClient> {
        OpenAIFunctionCallingClient(
            config = get(),
            httpClient = get(),
            delegate = get<LLMClient>()
        )
    }

    singleOf(::PlayerTagManager)

    // AIToolRegistry 创建后立即注册 5 个内置工具（含 3 个需要 Manager 的工具）
    single<AIToolRegistry> {
        AIToolRegistry().apply {
            BuiltinAITools.registerAll(
                registry = this,
                mapManager = get(),
                npcManager = get(),
                questManager = get()
            )
        }
    }

    singleOf(::AISceneManager)
}

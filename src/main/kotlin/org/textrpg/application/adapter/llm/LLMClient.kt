package org.textrpg.application.adapter.llm

import org.textrpg.application.adapter.llm.model.LLMMessage

interface LLMClient {
    suspend fun generate(messages: List<LLMMessage>): Result<String>

    fun shutdown()
}

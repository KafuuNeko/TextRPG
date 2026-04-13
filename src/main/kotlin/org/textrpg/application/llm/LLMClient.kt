package org.textrpg.application.llm

import org.textrpg.application.llm.model.LLMMessage

interface LLMClient {
    suspend fun generate(messages: List<LLMMessage>): Result<String>

    fun shutdown()
}
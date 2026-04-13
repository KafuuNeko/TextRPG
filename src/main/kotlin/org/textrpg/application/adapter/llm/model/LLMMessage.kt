package org.textrpg.application.adapter.llm.model

sealed class LLMMessage {
    data class System(val message: String) : LLMMessage()
    data class Assistant(val message: String) : LLMMessage()
    data class User(val message: String) : LLMMessage()
}

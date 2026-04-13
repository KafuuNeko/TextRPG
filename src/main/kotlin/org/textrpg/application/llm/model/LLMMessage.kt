package org.textrpg.application.llm.model

sealed class LLMMessage {
    data class System(val message: String) : LLMMessage()
    data class Assistant(val message: String) : LLMMessage()
    data class User(val message: String) : LLMMessage()
}

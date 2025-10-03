package com.vyibc.codeassistant.chat.model

import java.util.UUID

/**
 * 聊天会话模型
 */
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val className: String,
    val filePath: String,
    val createdAt: Long = System.currentTimeMillis(),
    var lastActiveAt: Long = System.currentTimeMillis(),
    val messages: MutableList<ChatMessage> = mutableListOf(),
    var lastCodeSnapshot: SessionCodeSnapshot? = null,
    val metadata: MutableMap<String, Any> = mutableMapOf()
) {
    fun addMessage(message: ChatMessage) {
        messages.add(message)
        lastActiveAt = System.currentTimeMillis()

        val maxMessages = 50
        if (messages.size > maxMessages) {
            messages.removeAt(0)
        }
    }

    fun getDisplayName(): String = className.substringAfterLast('.')

    fun getMessageCount(): Int = messages.size
}

/**
 * 最近一次代码选区快照
 */
data class SessionCodeSnapshot(
    val filePath: String,
    val className: String,
    val methodName: String?,
    val selectedCode: String,
    val selectionStart: Int,
    val selectionEnd: Int
)

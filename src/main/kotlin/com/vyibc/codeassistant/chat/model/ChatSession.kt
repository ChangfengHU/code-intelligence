package com.vyibc.codeassistant.chat.model

import com.intellij.openapi.util.TextRange
import java.util.*

/**
 * 聊天会话模型
 */
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val className: String,                         // 类全限定路径
    val filePath: String,                         // 文件路径
    val createdAt: Long = System.currentTimeMillis(),
    var lastActiveAt: Long = System.currentTimeMillis(),
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val metadata: MutableMap<String, Any> = mutableMapOf()
) {
    fun addMessage(message: ChatMessage) {
        messages.add(message)
        lastActiveAt = System.currentTimeMillis()
        
        // 限制消息数量（默认50条）
        val maxMessages = 50 // 后续从配置读取
        if (messages.size > maxMessages) {
            messages.removeAt(0)
        }
    }
    
    fun getDisplayName(): String {
        return className.substringAfterLast('.')
    }
    
    fun getMessageCount(): Int = messages.size
}
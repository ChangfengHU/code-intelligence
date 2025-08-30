package com.vyibc.codeassistant.chat.model

import com.intellij.openapi.util.TextRange
import java.util.*

/**
 * 聊天消息模型
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val type: MessageType,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val codeContext: CodeContext? = null,
    val metadata: MutableMap<String, Any> = mutableMapOf()
)

/**
 * 消息类型枚举
 */
enum class MessageType {
    USER,           // 用户消息
    ASSISTANT,      // AI助手消息
    SYSTEM,         // 系统消息
    CODE_ANALYSIS   // 代码分析消息（首次自动分析）
}
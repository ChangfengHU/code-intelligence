package com.vyibc.codeassistant.chat.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.vyibc.codeassistant.chat.settings.CodeChatSettings
import com.vyibc.codeassistant.chat.model.ChatMessage
import com.vyibc.codeassistant.chat.model.ChatSession
import com.vyibc.codeassistant.chat.model.CodeContext
import com.vyibc.codeassistant.chat.model.MessageType
import java.util.concurrent.ConcurrentHashMap

/**
 * 会话管理服务
 */
@Service(Service.Level.PROJECT)
class SessionManager {
    
    private val sessions = ConcurrentHashMap<String, ChatSession>()
    private val chatConfig = CodeChatSettings.getInstance().state
    
    /**
     * 获取或创建会话
     */
    fun getOrCreateSession(className: String, filePath: String): ChatSession {
        val existingSession = sessions.values.find { it.className == className }
        if (existingSession != null) {
            existingSession.lastActiveAt = System.currentTimeMillis()
            return existingSession
        }
        
        // 检查会话数量限制
        if (sessions.size >= chatConfig.maxSessions) {
            cleanupOldSessions()
        }
        
        val newSession = ChatSession(
            className = className,
            filePath = filePath
        )
        
        sessions[newSession.id] = newSession
        return newSession
    }
    
    /**
     * 保存会话
     */
    fun saveSession(session: ChatSession) {
        // 检查消息数量限制
        if (session.messages.size > chatConfig.maxMessagesPerSession) {
            // 保留最近的消息
            val messagesToKeep = session.messages.takeLast(chatConfig.maxMessagesPerSession)
            session.messages.clear()
            session.messages.addAll(messagesToKeep)
        }
        
        session.lastActiveAt = System.currentTimeMillis()
        sessions[session.id] = session
    }
    
    /**
     * 获取会话
     */
    fun getSession(sessionId: String): ChatSession? {
        return sessions[sessionId]
    }
    
    /**
     * 获取所有会话（按最后活跃时间排序）
     */
    fun getAllSessions(): List<ChatSession> {
        return sessions.values.sortedByDescending { it.lastActiveAt }
    }
    
    /**
     * 删除会话
     */
    fun deleteSession(sessionId: String): Boolean {
        return sessions.remove(sessionId) != null
    }
    
    /**
     * 清理旧会话
     */
    private fun cleanupOldSessions() {
        val sortedSessions = sessions.values.sortedBy { it.lastActiveAt }
        val sessionsToRemove = sortedSessions.take(sessions.size - chatConfig.maxSessions + 10)
        
        sessionsToRemove.forEach { session ->
            sessions.remove(session.id)
        }
    }
    
    /**
     * 创建首次代码分析消息
     */
    fun createInitialAnalysisMessage(codeContext: CodeContext): ChatMessage {
        return ChatMessage(
            type = MessageType.CODE_ANALYSIS,
            content = "请分析这段代码的作用和关键实现点",
            codeContext = codeContext
        )
    }
    
    /**
     * 获取会话的对话历史（用于传递给AI）
     */
    fun getConversationHistory(sessionId: String, maxMessages: Int = 10): List<ChatMessage> {
        val session = sessions[sessionId] ?: return emptyList()
        
        // 只获取用户和助手的消息，排除系统消息
        val conversationMessages = session.messages.filter { 
            it.type == MessageType.USER || it.type == MessageType.ASSISTANT || it.type == MessageType.CODE_ANALYSIS
        }
        
        // 返回最近的N条消息
        return conversationMessages.takeLast(maxMessages)
    }
    
    companion object {
        fun getInstance(): SessionManager = service()
    }
}
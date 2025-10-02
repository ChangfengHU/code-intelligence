package com.vyibc.codeassistant.chat.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
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
    private val persistence = service<ChatPersistence>()
    private val chatConfig = CodeChatSettings.getInstance().state
    private val sessions = ConcurrentHashMap<String, ChatSession>()

    init {
        val persisted = persistence.loadSessions(chatConfig.maxMessagesPerSession)
        persisted.forEach { session ->
            sessions[session.className] = session
        }
    }
    
    /**
     * 获取或创建会话（基于类全限定路径）
     */
    fun getOrCreateSession(className: String, filePath: String): ChatSession {
        // 使用类全限定路径作为会话标识
        val sessionKey = className

        // 查找现有会话
        val existingSession = sessions[sessionKey]
        if (existingSession != null) {
            existingSession.lastActiveAt = System.currentTimeMillis()
            return existingSession
        }
        
        // 检查会话数量限制
        if (sessions.size >= chatConfig.maxSessions) {
            cleanupOldSessions()
        }
        
        val newSession = ChatSession(
            className = sessionKey, // 使用类全限定路径
            filePath = filePath
        )
        
        sessions[sessionKey] = newSession
        persistence.saveOrUpdate(newSession, chatConfig.maxMessagesPerSession)
        return newSession
    }
    
    /**
     * 保存会话
     */
    fun saveSession(session: ChatSession) {
        // 这里可以接入 PersistentStateComponent 或本地文件持久化
        // 当前版本保存在内存中，并按配置条数限制消息条目
        // 检查消息数量限制
        if (session.messages.size > chatConfig.maxMessagesPerSession) {
            // 保留最近的消息
            val messagesToKeep = session.messages.takeLast(chatConfig.maxMessagesPerSession)
            session.messages.clear()
            session.messages.addAll(messagesToKeep)
        }
        
        session.lastActiveAt = System.currentTimeMillis()
        sessions[session.className] = session
        persistence.saveOrUpdate(session, chatConfig.maxMessagesPerSession)
    }
    
    /**
     * 获取会话
     */
    fun getSession(sessionId: String): ChatSession? {
        return sessions.values.find { it.id == sessionId }
    }

    fun findSessionByClassName(className: String): ChatSession? {
        return sessions[className]
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
        val entry = sessions.entries.find { it.value.id == sessionId } ?: return false
        sessions.remove(entry.key)
        persistence.deleteById(sessionId)
        return true
    }
    
    /**
     * 清理旧会话
     */
    private fun cleanupOldSessions() {
        val sortedSessions = sessions.values.sortedBy { it.lastActiveAt }
        val excess = sessions.size - chatConfig.maxSessions
        if (excess <= 0) {
            return
        }
        val sessionsToRemove = sortedSessions.take(excess)

        sessionsToRemove.forEach { session ->
            sessions.remove(session.className)
            persistence.deleteById(session.id)
        }
        
        println("清理了 ${sessionsToRemove.size} 个旧会话，当前会话数: ${sessions.size}")
    }
    
    /**
     * 清理会话中的旧消息
     */
    fun cleanupOldMessages(sessionId: String) {
        val session = sessions.values.find { it.id == sessionId } ?: return
        
        if (session.messages.size > chatConfig.maxMessagesPerSession) {
            val messagesToKeep = session.messages.takeLast(chatConfig.maxMessagesPerSession)
            session.messages.clear()
            session.messages.addAll(messagesToKeep)
            
            println("会话 ${session.className} 清理了旧消息，保留最近 ${messagesToKeep.size} 条消息")
        }
    }
    
    /**
     * 获取会话统计信息
     */
    fun getSessionStats(): Map<String, Any> {
        val totalSessions = sessions.size
        val totalMessages = sessions.values.sumOf { it.messages.size }
        val oldestSession = sessions.values.minByOrNull { it.lastActiveAt }
        val newestSession = sessions.values.maxByOrNull { it.lastActiveAt }
        
        return mapOf(
            "totalSessions" to totalSessions,
            "totalMessages" to totalMessages,
            "maxSessions" to chatConfig.maxSessions,
            "maxMessagesPerSession" to chatConfig.maxMessagesPerSession,
            "oldestSession" to (oldestSession?.className ?: "无"),
            "newestSession" to (newestSession?.className ?: "无")
        )
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
        val session = sessions.values.find { it.id == sessionId } ?: return emptyList()
        
        // 只获取用户和助手的消息，排除系统消息
        val conversationMessages = session.messages.filter { 
            it.type == MessageType.USER || it.type == MessageType.ASSISTANT || it.type == MessageType.CODE_ANALYSIS
        }
        
        // 返回最近的N条消息
        return conversationMessages.takeLast(maxMessages)
    }
    
    companion object {
        fun getInstance(project: Project): SessionManager = project.service()
    }
}

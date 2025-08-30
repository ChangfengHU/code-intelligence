package com.vyibc.codeassistant.chat.config

import com.vyibc.codeassistant.chat.settings.CodeChatSettings

/**
 * 聊天助手配置
 */
data class ChatConfig(
    val settings: CodeChatSettings.State = CodeChatSettings.getInstance().state
) {
    val systemPrompt: String get() = settings.systemPrompt
    val maxTokens: Int get() = settings.maxTokens
    val temperature: Double get() = settings.temperature
    val maxSessions: Int get() = settings.maxSessions
    val maxMessagesPerSession: Int get() = settings.maxMessagesPerSession
    val autoSaveInterval: Int get() = settings.autoSaveInterval
    val showTimestamp: Boolean get() = settings.showTimestamp
    val enableCodeHighlight: Boolean get() = settings.enableCodeHighlight
    val dialogWidth: Int get() = settings.dialogWidth
    val dialogHeight: Int get() = settings.dialogHeight
    val showHistoryOnStart: Boolean get() = settings.showHistoryOnStart
    val showAIInteraction: Boolean get() = settings.showAIInteraction
    val enableDebugMode: Boolean get() = settings.enableDebugMode
}
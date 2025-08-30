package com.vyibc.codeassistant.chat.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * 代码对话设置
 */
@State(
    name = "CodeChatSettings",
    storages = [Storage("CodeChatSettings.xml")]
)
@Service
class CodeChatSettings : PersistentStateComponent<CodeChatSettings.State> {
    
    data class State(
        // 系统提示词配置
        var systemPrompt: String = getDefaultSystemPrompt(),
        
        // AI配置
        var maxTokens: Int = 4000,
        var temperature: Double = 0.3,
        
        // 会话管理配置
        var maxSessions: Int = 100,
        var maxMessagesPerSession: Int = 50,
        var autoSaveInterval: Int = 30, // 秒
        
        // UI配置
        var showTimestamp: Boolean = true,
        var enableCodeHighlight: Boolean = true,
        var dialogWidth: Int = 800,
        var dialogHeight: Int = 600,
        var showHistoryOnStart: Boolean = true,
        
        // 调试配置
        var showAIInteraction: Boolean = false,
        var enableDebugMode: Boolean = false
    )
    
    private var myState = State()
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }
    
    companion object {
        fun getInstance(): CodeChatSettings = service()
        
        fun getDefaultSystemPrompt(): String = """
你是一个专业的代码分析助手，擅长分析多种编程语言代码。请根据用户选中的代码和提供的上下文信息，进行深入分析和解答。

上下文信息包括：
1. 用户选中的代码片段
2. 当前文件的完整代码
3. 相关的导入语句（如果适用）
4. 依赖的类和方法（如果适用）
5. 调用链路信息（如果适用）

请以专业、准确、易懂的方式回答用户的问题。如果需要，可以：
- 解释代码的功能和实现原理
- 分析代码的设计模式和最佳实践
- 指出潜在的问题或改进建议
- 解释与其他模块的交互关系
- 提供代码优化建议

回答请使用中文，保持简洁明了。当用户首次选择代码时，请主动分析这段代码的作用和关键点。

支持的语言包括但不限于：Java, Kotlin, Python, JavaScript, TypeScript, Go, C/C++等。
        """.trimIndent()
    }
}
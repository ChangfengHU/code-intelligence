package com.vyibc.codeassistant.chat.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.*
import com.vyibc.codeassistant.chat.config.ChatConfig
import com.vyibc.codeassistant.chat.model.ChatMessage
import com.vyibc.codeassistant.chat.model.ChatSession
import com.vyibc.codeassistant.chat.model.CodeContext
import com.vyibc.codeassistant.chat.model.MessageType
import com.vyibc.codeassistant.chat.service.AIConversationService
import com.vyibc.codeassistant.chat.service.SessionManager
import com.vyibc.codeassistant.chat.settings.CodeChatSettings
import kotlinx.coroutines.*
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*

/**
 * 代码聊天对话框 - 优化版本
 */
class CodeChatDialog(
    private val project: Project,
    private val session: ChatSession,
    private val codeContext: CodeContext
) : DialogWrapper(project) {
    
    private lateinit var messagesPanel: JPanel
    private lateinit var scrollPane: JBScrollPane
    private lateinit var inputField: JTextArea
    private lateinit var sendButton: JButton
    private lateinit var historyButton: JButton
    private lateinit var clearButton: JButton
    private lateinit var debugPanel: JPanel
    private lateinit var debugArea: JBTextArea
    
    private val sessionManager = SessionManager.getInstance(project)
    private val aiService = AIConversationService.getInstance()
    private val chatSettings = CodeChatSettings.getInstance()
    private val config = ChatConfig(chatSettings.state)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    init {
        title = "代码问答助手 - ${getSessionDisplayName(session)}"
        
        // 使用配置中的对话框尺寸
        setSize(config.dialogWidth, config.dialogHeight)
        
        println("初始化CodeChatDialog, session.messages.size=${session.messages.size}")
        println("config.showHistoryOnStart=${config.showHistoryOnStart}")
        println("codeContext.selectedCode.length=${codeContext.selectedCode.length}")
        
        init() // 这会调用createCenterPanel
        
        // 在UI初始化完成后加载内容
        SwingUtilities.invokeLater {
            println("开始加载对话内容...")
            println("messagesPanel: $messagesPanel")
            println("messagesPanel.layout: ${messagesPanel.layout}")
            println("messagesPanel.size: ${messagesPanel.size}")
            println("messagesPanel.preferredSize: ${messagesPanel.preferredSize}")
            
            // 先添加一个启动消息
            val startupMessage = ChatMessage(
                type = MessageType.SYSTEM,
                content = "🚀 代码问答助手已启动！\n\n正在初始化系统..."
            )
            addMessageToUI(startupMessage, false)
            
            // 添加一个测试消息，确保UI能正确显示
            val testMessage = ChatMessage(
                type = MessageType.ASSISTANT,
                content = "这是一个测试消息，用于验证消息显示功能。如果你能看到这条消息，说明UI显示正常。"
            )
            addMessageToUI(testMessage, false)
            
            // 加载历史消息
            if (config.showHistoryOnStart && session.messages.isNotEmpty()) {
                println("加载历史消息...")
                loadHistoryMessages()
            }
            
            // 如果是新会话或没有消息，自动进行首次分析
            if (session.messages.isEmpty() && codeContext.selectedCode.isNotEmpty()) {
                println("会话为空，开始初始分析...")
                // 使用SwingTimer延迟执行初始分析，确保UI完全初始化
                val delayTimer = Timer(2000) {
                    println("定时器触发，开始执行初始分析...")
                    // 先清除启动消息和测试消息
                    removeMessageFromUI(startupMessage)
                    removeMessageFromUI(testMessage)
                    
                    performInitialAnalysis()
                }
                delayTimer.isRepeats = false
                delayTimer.start()
                println("初始分析定时器已启动")
            } else {
                println("会话已有${session.messages.size}条消息")
            }
        }
    }
    
    override fun createCenterPanel(): JComponent {
        // 仅聊天面板（去除左侧代码预览），更简洁美观
        val chatMainPanel = JPanel(BorderLayout()).apply {
            background = JBColor.background()
        }

        // 消息显示区域（使用内边距和更自然的背景）
        messagesPanel = JPanel()
        messagesPanel.layout = BoxLayout(messagesPanel, BoxLayout.Y_AXIS)
        messagesPanel.background = JBColor.background()
        messagesPanel.alignmentX = 0.0f
        messagesPanel.border = BorderFactory.createEmptyBorder(10, 12, 10, 12)

        scrollPane = JBScrollPane(messagesPanel)
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.border = BorderFactory.createEmptyBorder()
        scrollPane.background = JBColor.background()

        val inputPanel = createInputPanel()

        chatMainPanel.add(scrollPane, BorderLayout.CENTER)
        chatMainPanel.add(inputPanel, BorderLayout.SOUTH)

        // 可选调试面板
        return if (config.enableDebugMode) {
            debugPanel = createDebugPanel()
            JSplitPane(JSplitPane.VERTICAL_SPLIT, chatMainPanel, debugPanel).apply { resizeWeight = 0.82 }
        } else chatMainPanel
    }
    
    private fun createInputPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        panel.background = JBColor.namedColor("Editor.inputBackground", JBColor.background())
        
        // 输入文本区域
        inputField = JTextArea(3, 50)
        inputField.lineWrap = true
        inputField.isEnabled = true
        inputField.wrapStyleWord = true
        inputField.font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
        inputField.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border()),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        )
        
        val inputScrollPane = JBScrollPane(inputField)
        inputScrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        
        // 按钮面板
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        
        sendButton = JButton("发送")
        sendButton.preferredSize = Dimension(80, 35)
        sendButton.addActionListener { sendMessage() }
        sendButton.isEnabled = true
        
        clearButton = JButton("清空")
        clearButton.preferredSize = Dimension(80, 35)
        clearButton.addActionListener { clearInput() }
        
        historyButton = JButton("会话历史")
        historyButton.preferredSize = Dimension(100, 35)
        historyButton.addActionListener { showSessionHistory() }
        
        buttonPanel.add(historyButton)
        buttonPanel.add(clearButton)
        buttonPanel.add(sendButton)
        
        // 布局
        val title = JBLabel("💬 请输入您的问题")
        title.border = BorderFactory.createEmptyBorder(0, 0, 6, 0)
        panel.add(title, BorderLayout.NORTH)
        panel.add(inputScrollPane, BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        
        // Enter键发送
        inputField.inputMap.put(KeyStroke.getKeyStroke("ctrl ENTER"), "send")
        inputField.inputMap.put(KeyStroke.getKeyStroke("ENTER"), "send")
        inputField.actionMap.put("send", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                sendMessage()
            }
        })
        
        return panel
    }
    
    private fun createDebugPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("AI交互调试信息")
        
        debugArea = JBTextArea()
        debugArea.isEditable = false
        debugArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        debugArea.background = JBColor.background()
        
        val debugScrollPane = JBScrollPane(debugArea)
        debugScrollPane.preferredSize = Dimension(-1, 200)
        
        panel.add(debugScrollPane, BorderLayout.CENTER)
        return panel
    }
    
    private fun loadHistoryMessages() {
        session.messages.forEach { message ->
            addMessageToUI(message, false) // false因为已经在session中了
        }
        scrollToBottom()
    }
    
    private fun performInitialAnalysis() {
        println("进入performInitialAnalysis, selectedCode.length=${codeContext.selectedCode.length}")
        
        if (codeContext.selectedCode.isEmpty()) {
            println("没有选中代码，显示欢迎消息")
            // 如果没有选中代码，显示欢迎消息
            val welcomeMessage = ChatMessage(
                type = MessageType.SYSTEM,
                content = "欢迎使用代码对话助手！\n\n请选择一段代码后再次点击菜单，或者直接在下方输入框中提问。\n\n💡 提示：选择代码后，我会自动分析代码的作用和关键点。"
            )
            addMessageToUI(welcomeMessage, false)
            println("欢迎消息已添加")
            return
        }
        
        println("有选中代码，开始协程分析...")
        
        coroutineScope.launch {
            var loadingMessage: ChatMessage? = null
            try {
                println("协程开始执行")
                sendButton.isEnabled = false
                
                // 添加初始分析消息
                val analysisMessage = ChatMessage(
                    type = MessageType.CODE_ANALYSIS,
                    content = "🔍 检测到选中代码，正在进行深度分析...",
                    codeContext = codeContext
                )
                addMessageToUI(analysisMessage, true)
                println("初始分析消息已添加")
                
                // 添加加载状态
                loadingMessage = ChatMessage(
                    type = MessageType.SYSTEM,
                    content = "⚙️ AI正在深度分析代码的作用、实现原理和关键点，请稍候..."
                )
                addMessageToUI(loadingMessage, false)
                println("加载消息已添加")
                
                addDebugInfo("开始自动代码分析...")
                
                if (config.showAIInteraction) {
                    addDebugInfo("选中代码长度: ${codeContext.selectedCode.length}")
                    addDebugInfo("文件类型: ${codeContext.className}")
                    addDebugInfo("方法名: ${codeContext.methodName ?: "未知"}")
                    addDebugInfo("依赖类数量: ${codeContext.dependencies.size}")
                    addDebugInfo("调用链数量: ${codeContext.callChain.size}")
                    addDebugInfo("开始调用OpenAI API进行深度分析...")
                }
                
                println("开始调用AI服务...")
                
                // 构建更详细的初始分析提示
                val initialPrompt = buildInitialAnalysisPrompt()
                
                // 调用AI进行初始分析
                val aiResponse = withContext(Dispatchers.IO) {
                    aiService.sendMessage(
                        initialPrompt,
                        codeContext,
                        session.messages
                    )
                }
                
                println("AI响应收到，长度: ${aiResponse.length}")
                
                // 移除加载消息
                if (loadingMessage != null) {
                    removeMessageFromUI(loadingMessage)
                    println("加载消息已移除")
                }
                
                // 添加AI回复
                val aiMessage = ChatMessage(
                    type = MessageType.ASSISTANT,
                    content = aiResponse
                )
                
                addMessageToUI(aiMessage, true)
                println("AI回复消息已添加")
                
                addDebugInfo("AI分析完成，响应长度: ${aiResponse.length}")
                
                if (config.showAIInteraction) {
                    addDebugInfo("分析结果预览: ${aiResponse.take(100)}...")
                }
                
            } catch (e: Exception) {
                println("初始分析发生异常: ${e.message}")
                e.printStackTrace()
                
                // 移除加载消息（如果存在）
                if (loadingMessage != null) {
                    removeMessageFromUI(loadingMessage)
                }
                
                val errorMessage = ChatMessage(
                    type = MessageType.SYSTEM,
                    content = "⚠️ 自动分析失败：${e.message}\n\n请检查：\n1. 网络连接是否正常\n2. API Key 是否在 Tools → Code Assistant → 代码翻译(AI) 中正确配置\n3. 网络代理设置是否正确\n\n您可以直接在下方输入框中提问。"
                )
                addMessageToUI(errorMessage, true)
                addDebugInfo("AI分析失败: ${e.message}")
                
                println("错误消息已添加")
            } finally {
                sendButton.isEnabled = true
                println("初始分析完成，按钮已启用")
            }
        }
    }
    
    /**
     * 构建初始分析提示词
     */
    private fun buildInitialAnalysisPrompt(): String {
        return """
请对这段代码进行深度分析，包括：

1. **功能分析**：这段代码的主要作用是什么？
2. **实现原理**：代码是如何实现其功能的？
3. **关键点**：代码中的关键逻辑和重要细节
4. **设计模式**：是否使用了特定的设计模式？
5. **潜在问题**：是否存在潜在的问题或改进空间？
6. **依赖关系**：与其他类或方法的交互关系

请提供详细、准确的分析，帮助开发者更好地理解这段代码。
        """.trimIndent()
    }
    
    private fun sendMessage() {
        val userInput = inputField.text.trim()
        if (userInput.isEmpty()) return
        
        coroutineScope.launch {
            var loadingMessage: ChatMessage? = null
            try {
                sendButton.isEnabled = false
                inputField.isEnabled = false
                
                // 添加用户消息
                val userMessage = ChatMessage(
                    type = MessageType.USER,
                    content = userInput,
                    codeContext = if (codeContext.selectedCode.isNotEmpty()) codeContext else null
                )
                
                addMessageToUI(userMessage, true)
                clearInput()
                addDebugInfo("发送用户消息: ${userInput.take(50)}...")
                
                // 添加加载状态消息
                loadingMessage = ChatMessage(
                    type = MessageType.SYSTEM,
                    content = "🔄 正在分析您的问题并生成回答，请稍候..."
                )
                addMessageToUI(loadingMessage, false) // 不保存到会话中
                
                // 显示请求详情（如果启用）
                if (config.showAIInteraction) {
                    addDebugInfo("构建AI请求上下文...")
                    addDebugInfo("选中代码长度: ${codeContext.selectedCode.length}")
                    addDebugInfo("对话历史消息数: ${session.messages.size}")
                    addDebugInfo("开始调用OpenAI API...")
                }
                
                // 调用AI服务
                val aiResponse = withContext(Dispatchers.IO) {
                    aiService.sendMessage(userInput, codeContext, session.messages)
                }
                
                // 移除加载消息
                if (loadingMessage != null) {
                    removeMessageFromUI(loadingMessage)
                }
                
                // 添加AI回复
                val aiMessage = ChatMessage(
                    type = MessageType.ASSISTANT,
                    content = aiResponse
                )
                
                addMessageToUI(aiMessage, true)
                addDebugInfo("收到AI响应，长度: ${aiResponse.length}")
                
                // 显示响应详情（如果启用）
                if (config.showAIInteraction) {
                    addDebugInfo("AI响应预览: ${aiResponse.take(100)}...")
                }
                
            } catch (e: Exception) {
                // 移除加载消息（如果存在）
                if (loadingMessage != null) {
                    removeMessageFromUI(loadingMessage)
                }
                
                val errorMessage = ChatMessage(
                    type = MessageType.SYSTEM,
                    content = "⚠️ 发送失败：${e.message}\n\n请检查：\n1. 网络连接是否正常\n2. API Key 是否在 Tools → Code Assistant → 代码翻译(AI) 中正确配置\n3. 网络代理设置是否正确"
                )
                addMessageToUI(errorMessage, true)
                addDebugInfo("发送失败: ${e.message}")
            } finally {
                sendButton.isEnabled = true
                inputField.isEnabled = true
                inputField.requestFocus()
            }
        }
    }
    
    // 维护一个映射用于跟踪组件
    private val messageComponentMap = mutableMapOf<ChatMessage, MessageComponent>()
    
    private fun addMessageToUI(message: ChatMessage, saveToSession: Boolean) {
        println("添加消息到UI: type=${message.type}, content长度=${message.content.length}, saveToSession=$saveToSession")
        
        SwingUtilities.invokeLater {
            try {
                val messageComponent = MessageComponent(message)
                
                // 记录组件映射
                messageComponentMap[message] = messageComponent
                
                println("创建MessageComponent成功，preferredSize: ${messageComponent.preferredSize}")
                println("MessageComponent大小: ${messageComponent.size}")
                println("MessageComponent可见性: ${messageComponent.isVisible}")
                
                // 添加到消息面板
                messagesPanel.add(messageComponent)
                println("组件已添加到messagesPanel, 当前组件数: ${messagesPanel.componentCount}")
                
                // 确保组件可见
                messageComponent.isVisible = true
                messageComponent.isOpaque = true
                
                // 强制刷新UI
                messagesPanel.invalidate()
                messagesPanel.revalidate() 
                messagesPanel.repaint()
                
                // 强制刷新滚动面板
                scrollPane.invalidate()
                scrollPane.revalidate()
                scrollPane.repaint()
                
                // 强制刷新主面板
                this@CodeChatDialog.contentPanel?.invalidate()
                this@CodeChatDialog.contentPanel?.revalidate()
                this@CodeChatDialog.contentPanel?.repaint()
                
                println("UI刷新完成，messagesPanel大小: ${messagesPanel.size}")
                println("scrollPane大小: ${scrollPane.size}")
                
                // 滚动到底部
                scrollToBottom()
                
                if (saveToSession) {
                    session.messages.add(message)
                    sessionManager.saveSession(session)
                    println("消息已保存到会话，当前会话消息数: ${session.messages.size}")
                }
            } catch (e: Exception) {
                println("添加消息到UI失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private fun removeMessageFromUI(message: ChatMessage) {
        SwingUtilities.invokeLater {
            val component = messageComponentMap[message]
            if (component != null) {
                messagesPanel.remove(component)
                messageComponentMap.remove(message)
                messagesPanel.revalidate()
                messagesPanel.repaint()
            }
        }
    }
    
    private fun addDebugInfo(info: String) {
        if (config.enableDebugMode && ::debugArea.isInitialized) {
            SwingUtilities.invokeLater {
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())
                debugArea.append("[$timestamp] $info\n")
                debugArea.caretPosition = debugArea.document.length
            }
        }
    }
    
    private fun clearInput() {
        inputField.text = ""
    }
    
    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            try {
                val verticalScrollBar = scrollPane.verticalScrollBar
                if (verticalScrollBar.isVisible) {
                    verticalScrollBar.value = verticalScrollBar.maximum
                }
                println("滚动到底部，最大值: ${verticalScrollBar.maximum}, 当前值: ${verticalScrollBar.value}")
            } catch (e: Exception) {
                println("滚动到底部失败: ${e.message}")
            }
        }
    }
    
    private fun showSessionHistory() {
        val historyDialog = SessionHistoryDialog(project, session)
        historyDialog.show()
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(createCloseAction())
    }
    
    private fun createCloseAction(): Action {
        return object : AbstractAction("关闭") {
            override fun actionPerformed(e: ActionEvent?) {
                close(OK_EXIT_CODE)
            }
        }
    }
    
    override fun dispose() {
        coroutineScope.cancel()
        super.dispose()
    }
    
    // 获取显示名称的工具方法
    private fun getSessionDisplayName(session: ChatSession): String {
        val shortClassName = session.className.substringAfterLast('.')
        return if (shortClassName.isNotEmpty()) shortClassName else session.className
    }
}
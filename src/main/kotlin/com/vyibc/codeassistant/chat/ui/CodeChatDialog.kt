package com.vyibc.codeassistant.chat.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.vyibc.codeassistant.chat.config.ChatConfig
import com.vyibc.codeassistant.chat.model.ChatMessage
import com.vyibc.codeassistant.chat.model.ChatSession
import com.vyibc.codeassistant.chat.model.CodeContext
import com.vyibc.codeassistant.chat.model.MessageType
import com.vyibc.codeassistant.chat.service.AIConversationService
import com.vyibc.codeassistant.chat.service.SessionManager
import com.vyibc.codeassistant.chat.settings.CodeChatSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Insets
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

class CodeChatDialog(
    private val project: Project,
    private val session: ChatSession,
    private val codeContext: CodeContext
) : DialogWrapper(project) {

    private val sessionManager = SessionManager.getInstance(project)
    private val aiService = AIConversationService.getInstance()
    private val chatSettings = CodeChatSettings.getInstance()
    private val config = ChatConfig(chatSettings.state)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var messagesPanel: JPanel
    private lateinit var scrollPane: JBScrollPane
    private lateinit var inputField: JTextArea
    private lateinit var sendButton: JButton
    private lateinit var historyButton: JButton

    private val componentByMessageId = mutableMapOf<String, MessageComponent>()

    init {
        title = "代码问答助手"
        setSize(config.dialogWidth, config.dialogHeight)
        isResizable = true
        okAction.putValue(Action.NAME, "关闭")
        init()
        SwingUtilities.invokeLater {
            loadHistory()
            if (session.messages.isEmpty()) {
                startAutoAnalysisIfNeeded()
            } else {
                scrollToBottom()
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val container = JPanel(BorderLayout())
        container.background = backgroundColor()

        container.add(createHeaderPanel(), BorderLayout.NORTH)

        messagesPanel = JPanel()
        messagesPanel.layout = javax.swing.BoxLayout(messagesPanel, javax.swing.BoxLayout.Y_AXIS)
        messagesPanel.isOpaque = false
        messagesPanel.border = BorderFactory.createEmptyBorder(12, 16, 12, 16)

        scrollPane = JBScrollPane(messagesPanel)
        scrollPane.border = BorderFactory.createEmptyBorder()
        scrollPane.verticalScrollBar.unitIncrement = 16
        scrollPane.background = backgroundColor()

        container.add(scrollPane, BorderLayout.CENTER)
        container.add(createInputPanel(), BorderLayout.SOUTH)

        return container
    }

    private fun createHeaderPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.isOpaque = false
        panel.border = BorderFactory.createEmptyBorder(18, 20, 12, 20)

        val titleLabel = JBLabel(session.className.substringAfterLast('.'))
        titleLabel.font = Font("SF Pro Display", Font.BOLD, 18)
        titleLabel.foreground = primaryTextColor()

        val subtitle = JBLabel(codeContext.className)
        subtitle.font = Font("SF Pro Text", Font.PLAIN, 12)
        subtitle.foreground = secondaryTextColor()
        subtitle.horizontalAlignment = SwingConstants.LEFT

        panel.add(titleLabel, BorderLayout.NORTH)
        panel.add(subtitle, BorderLayout.SOUTH)
        return panel
    }

    private fun createInputPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(12, 16, 16, 16)
        panel.background = backgroundColor()

        inputField = JBTextArea(3, 60)
        inputField.wrapStyleWord = true
        inputField.lineWrap = true
        inputField.margin = Insets(12, 14, 12, 14)
        inputField.font = Font("SF Pro Text", Font.PLAIN, 14)
        inputField.foreground = primaryTextColor()
        inputField.background = Color(255, 255, 255)
        inputField.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(0xE0E0E0, 0x3C3F41), 1, true),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)
        )

        val inputScrollPane = JBScrollPane(inputField)
        inputScrollPane.preferredSize = Dimension(0, 120)
        inputScrollPane.border = BorderFactory.createEmptyBorder()

        sendButton = JButton("发送")
        sendButton.preferredSize = Dimension(88, 36)
        sendButton.background = accentColor()
        sendButton.foreground = Color.WHITE
        sendButton.font = Font("SF Pro Text", Font.BOLD, 13)
        sendButton.isOpaque = true
        sendButton.border = BorderFactory.createEmptyBorder(6, 12, 6, 12)
        sendButton.addActionListener { sendMessage() }

        historyButton = JButton("历史")
        historyButton.preferredSize = Dimension(88, 36)
        historyButton.background = Color(0xF2F2F7)
        historyButton.foreground = primaryTextColor()
        historyButton.font = Font("SF Pro Text", Font.PLAIN, 13)
        historyButton.isOpaque = true
        historyButton.border = BorderFactory.createEmptyBorder(6, 12, 6, 12)
        historyButton.addActionListener { showSessionHistory() }

        val buttonPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0))
        buttonPanel.isOpaque = false
        buttonPanel.add(historyButton)
        buttonPanel.add(sendButton)

        panel.add(inputScrollPane, BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.EAST)

        inputField.inputMap.put(KeyStroke.getKeyStroke("ENTER"), "sendMessage")
        inputField.actionMap.put("sendMessage", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                if (e?.modifiers ?: 0 == 0) {
                    sendMessage()
                }
            }
        })
        inputField.inputMap.put(KeyStroke.getKeyStroke("shift ENTER"), "newline")
        inputField.actionMap.put("newline", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                inputField.insert("\n", inputField.caretPosition)
            }
        })

        return panel
    }

    private fun loadHistory() {
        session.messages.forEach { pushMessage(it, persist = false) }
    }

    private fun startAutoAnalysisIfNeeded() {
        if (codeContext.selectedCode.isBlank()) {
            val welcome = ChatMessage(
                type = MessageType.SYSTEM,
                content = "请选择代码或直接在下方提问，我会为你提供专业分析。"
            )
            pushMessage(welcome, persist = false)
            return
        }

        val prompt = buildAutoAnalysisPrompt()
        val analysisMessage = ChatMessage(
            type = MessageType.CODE_ANALYSIS,
            content = prompt,
            codeContext = codeContext
        )
        pushMessage(analysisMessage, persist = true)
        val historySnapshot = synchronized(session) { session.messages.toList() }
        requestAssistantResponse(prompt, codeContext, historySnapshot)
    }

    private fun sendMessage() {
        val userInput = inputField.text.trim()
        if (userInput.isEmpty()) return

        inputField.text = ""
        val userMessage = ChatMessage(
            type = MessageType.USER,
            content = userInput,
            codeContext = if (codeContext.selectedCode.isNotBlank()) codeContext else null
        )
        pushMessage(userMessage, persist = true)
        val historySnapshot = synchronized(session) { session.messages.toList() }
        requestAssistantResponse(userInput, codeContext, historySnapshot)
    }

    private fun requestAssistantResponse(
        prompt: String,
        context: CodeContext,
        historySnapshot: List<ChatMessage>
    ) {
        setInteractionEnabled(false)
        val loadingMessage = ChatMessage(
            type = MessageType.SYSTEM,
            content = "✨ 正在分析，请稍候…"
        )
        pushMessage(loadingMessage, persist = false)

        coroutineScope.launch {
            try {
                val aiReply = withContext(Dispatchers.IO) {
                    aiService.sendMessage(prompt, context, historySnapshot)
                }
                removeMessage(loadingMessage.id)
                val assistantMessage = ChatMessage(
                    type = MessageType.ASSISTANT,
                    content = aiReply
                )
                pushMessage(assistantMessage, persist = true)
            } catch (e: Exception) {
                removeMessage(loadingMessage.id)
                val errorMessage = ChatMessage(
                    type = MessageType.SYSTEM,
                    content = "⚠️ AI 回复失败：${e.message}"
                )
                pushMessage(errorMessage, persist = true)
            } finally {
                setInteractionEnabled(true)
            }
        }
    }

    private fun pushMessage(message: ChatMessage, persist: Boolean) {
        if (persist) {
            synchronized(session) {
                session.messages.add(message)
                sessionManager.saveSession(session)
            }
        }
        SwingUtilities.invokeLater {
            val messageComponent = MessageComponent(message)
            componentByMessageId[message.id] = messageComponent
            messagesPanel.add(messageComponent)
            messagesPanel.revalidate()
            messagesPanel.repaint()
            scrollToBottom()
        }
    }

    private fun removeMessage(messageId: String) {
        SwingUtilities.invokeLater {
            val component = componentByMessageId.remove(messageId) ?: return@invokeLater
            messagesPanel.remove(component)
            messagesPanel.revalidate()
            messagesPanel.repaint()
        }
    }

    private fun setInteractionEnabled(enabled: Boolean) {
        SwingUtilities.invokeLater {
            sendButton.isEnabled = enabled
            inputField.isEnabled = enabled
            if (enabled) {
                inputField.requestFocus()
            }
        }
    }

    private fun scrollToBottom() {
        val verticalBar: JScrollBar = scrollPane.verticalScrollBar
        verticalBar.value = verticalBar.maximum
    }

    private fun buildAutoAnalysisPrompt(): String {
        return """
请详细解读我刚才选中的代码片段，重点说明：
1. 代码实现的核心功能
2. 关键逻辑与执行流程
3. 涉及的重要类或方法之间的关系
4. 可能的边界情况、隐患与优化建议
5. 如有必要，可提供简洁示例帮助理解

请使用中文进行讲解，保持条理清晰、专业且易于理解。
        """.trimIndent()
    }

    private fun showSessionHistory() {
        val dialog = SessionHistoryDialog(project, session)
        dialog.show()
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun dispose() {
        super.dispose()
        coroutineScope.cancel()
    }

    private fun backgroundColor(): Color = JBColor(0xF5F5F7, 0x2B2D30)
    private fun primaryTextColor(): Color = JBColor(0x1C1C1E, 0xFFFFFF)
    private fun secondaryTextColor(): Color = JBColor(0x8E8E93, 0x8E8E93)
    private fun accentColor(): Color = JBColor(0x007AFF, 0x0A84FF)
}

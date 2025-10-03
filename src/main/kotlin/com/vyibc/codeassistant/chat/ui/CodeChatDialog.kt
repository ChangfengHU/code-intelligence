package com.vyibc.codeassistant.chat.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.TextRange
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
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Insets
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer

class CodeChatDialog(
    private val project: Project,
    private val session: ChatSession,
    private val codeContext: CodeContext,
    private val hasNewSelection: Boolean
) : DialogWrapper(project) {

    companion object {
        internal const val CODE_PROMPT_PREFIX = "请帮我分析以下代码片段并指出潜在问题："
    }

    private val sessionManager = SessionManager.getInstance(project)
    private val aiService = AIConversationService.getInstance()
    private val chatSettings = CodeChatSettings.getInstance()
    private val config = ChatConfig(chatSettings.state)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val activeCodeContext: CodeContext = resolveActiveCodeContext()
    private val displayMethod: String? = activeCodeContext.methodName

    private lateinit var messagesPanel: JPanel
    private lateinit var scrollPane: JBScrollPane
    private lateinit var inputField: JTextArea
    private lateinit var sendButton: JButton
    private lateinit var historyButton: JButton
    private lateinit var statusLabel: JBLabel

    private val componentByMessageId = mutableMapOf<String, MessageComponent>()
    private var statusResetTimer: Timer? = null

    init {
        title = "代码问答助手"
        val minWidth = 1024
        val minHeight = 680
        setSize(
            maxOf(config.dialogWidth, minWidth),
            maxOf(config.dialogHeight, minHeight)
        )
        isResizable = true
        okAction.putValue(Action.NAME, "关闭")

        init()
        SwingUtilities.invokeLater {
            loadHistory()
            handleStartupState()
        }
    }

    override fun createCenterPanel(): JComponent {
        val container = JPanel(BorderLayout()).apply {
            background = backgroundColor()
        }

        container.add(createHeaderPanel(), BorderLayout.NORTH)

        messagesPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = BorderFactory.createEmptyBorder(24, 48, 24, 48)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        scrollPane = JBScrollPane(messagesPanel).apply {
            border = BorderFactory.createEmptyBorder()
            verticalScrollBar.unitIncrement = 18
            background = backgroundColor()
            viewport.background = backgroundColor()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

        container.add(scrollPane, BorderLayout.CENTER)
        container.add(createInputPanel(), BorderLayout.SOUTH)
        return container
    }

    private fun createHeaderPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.isOpaque = false
        panel.border = BorderFactory.createEmptyBorder(24, 32, 18, 32)

        val titleLabel = JBLabel(session.className.substringAfterLast('.'))
        titleLabel.font = Font("SF Pro Display", Font.BOLD, 18)
        titleLabel.foreground = primaryTextColor()

        val subtitleText = buildString {
            append(activeCodeContext.className)
            displayMethod?.let { append(" · $it") }
        }
        val subtitle = JBLabel(subtitleText)
        subtitle.font = Font("SF Pro Text", Font.PLAIN, 12)
        subtitle.foreground = secondaryTextColor()
        subtitle.horizontalAlignment = SwingConstants.LEFT

        panel.add(titleLabel, BorderLayout.NORTH)
        panel.add(subtitle, BorderLayout.SOUTH)
        return panel
    }

    private fun createInputPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.background = surfaceColor()
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor()),
            BorderFactory.createEmptyBorder(20, 32, 24, 32)
        )

        inputField = JBTextArea(3, 60)
        inputField.wrapStyleWord = true
        inputField.lineWrap = true
        inputField.margin = Insets(12, 14, 12, 14)
        inputField.font = Font("SF Pro Text", Font.PLAIN, 14)
        inputField.foreground = inputForegroundColor()
        inputField.background = inputBackgroundColor()
        inputField.caretColor = accentColor()
        inputField.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(inputBorderColor(), 1, true),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)
        )

        val inputScrollPane = JBScrollPane(inputField)
        inputScrollPane.border = BorderFactory.createEmptyBorder()
        inputScrollPane.background = surfaceColor()
        inputScrollPane.viewport.background = inputBackgroundColor()
        inputScrollPane.verticalScrollBar.unitIncrement = 14
        inputScrollPane.preferredSize = Dimension(0, 140)

        sendButton = JButton("发送")
        sendButton.preferredSize = Dimension(96, 36)
        sendButton.background = accentColor()
        sendButton.foreground = Color.WHITE
        sendButton.font = Font("SF Pro Text", Font.BOLD, 13)
        sendButton.isOpaque = true
        sendButton.border = BorderFactory.createEmptyBorder(8, 16, 8, 16)
        sendButton.addActionListener { sendMessage() }

        historyButton = JButton("历史")
        historyButton.preferredSize = Dimension(96, 36)
        historyButton.background = surfaceColor()
        historyButton.foreground = secondaryTextColor()
        historyButton.font = Font("SF Pro Text", Font.PLAIN, 13)
        historyButton.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(inputBorderColor(), 1, true),
            BorderFactory.createEmptyBorder(6, 16, 6, 16)
        )
        historyButton.isOpaque = false
        historyButton.addActionListener { showSessionHistory() }

        val buttonPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 12, 0))
        buttonPanel.isOpaque = false
        buttonPanel.add(historyButton)
        buttonPanel.add(sendButton)

        panel.add(inputScrollPane, BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.EAST)

        statusLabel = JBLabel("")
        statusLabel.font = Font("SF Pro Text", Font.PLAIN, 12)
        statusLabel.foreground = secondaryTextColor()
        statusLabel.border = BorderFactory.createEmptyBorder(12, 2, 0, 0)

        val statusPanel = JPanel(BorderLayout())
        statusPanel.isOpaque = false
        statusPanel.add(statusLabel, BorderLayout.WEST)

        panel.add(statusPanel, BorderLayout.SOUTH)

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

    private fun handleStartupState() {
        val hasSelection = activeCodeContext.selectedCode.isNotBlank()

        if (session.messages.isEmpty() && !hasSelection) {
            val welcome = ChatMessage(
                type = MessageType.SYSTEM,
                content = "请选择代码或直接在下方提问，我会结合上下文为你解答。"
            )
            pushMessage(welcome, persist = false)
            updateStatus("已就绪，可直接向模型提问")
            return
        }

        if (!hasSelection) {
            scrollToBottom()
            updateStatus("已就绪，可直接向模型提问")
            return
        }

        when {
            session.messages.isEmpty() -> {
                insertSelectedCodeIntoInput()
                updateStatus("已将选中代码复制到输入框，点击发送即可。")
            }
            hasNewSelection -> {
                val notice = ChatMessage(
                    type = MessageType.SYSTEM,
                    content = buildString {
                        append("🔁 已载入新的代码片段")
                        displayMethod?.let { append("（$it）") }
                        append("，并据此继续会话。")
                    }
                )
                pushMessage(notice, persist = true)
                insertSelectedCodeIntoInput()
                updateStatus("已将新的选中代码复制到输入框，点击发送即可。")
            }
            else -> {
                scrollToBottom()
                updateStatus("已就绪，可继续追问或发送新问题")
            }
        }
    }

    private fun sendMessage() {
        val userInput = inputField.text.trim()
        if (userInput.isEmpty()) return

        val messageContext = determineContextForInput(userInput)

        inputField.text = ""
        val userMessage = ChatMessage(
            type = MessageType.USER,
            content = userInput,
            codeContext = messageContext
        )
        pushMessage(userMessage, persist = true)
        val historySnapshot = synchronized(session) { session.messages.toList() }
        requestAssistantResponse(userInput, messageContext, historySnapshot)
    }

    private fun requestAssistantResponse(
        prompt: String,
        context: CodeContext?,
        historySnapshot: List<ChatMessage>
    ) {
        setInteractionEnabled(false)
        updateStatus("正在请求模型…")
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
                updateStatusWithAutoClear("模型回复完成")
            } catch (e: Exception) {
                removeMessage(loadingMessage.id)
                val errorMessage = ChatMessage(
                    type = MessageType.SYSTEM,
                    content = "⚠️ AI 回复失败：${e.message}"
                )
                pushMessage(errorMessage, persist = true)
                updateStatus("AI 回复失败：${e.message}", isError = true)
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
            messageComponent.alignmentX = Component.LEFT_ALIGNMENT
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
            sendButton.text = if (enabled) "发送" else "发送中…"
            inputField.isEnabled = enabled
            historyButton.isEnabled = enabled
            if (enabled) {
                inputField.requestFocus()
            }
        }
    }

    private fun scrollToBottom() {
        val verticalBar: JScrollBar = scrollPane.verticalScrollBar
        verticalBar.value = verticalBar.maximum
    }

    private fun showSessionHistory() {
        val dialog = SessionHistoryDialog(project, session)
        dialog.show()
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun dispose() {
        super.dispose()
        coroutineScope.cancel()
        statusResetTimer?.stop()
    }

    private fun backgroundColor(): Color = JBColor(0xF5F5F7, 0x1F1F22)
    private fun surfaceColor(): Color = JBColor(0xFFFFFF, 0x2B2D31)
    private fun surfaceInsetColor(): Color = JBColor(0xF7F9FD, 0x232327)
    private fun surfaceInsetBorderColor(): Color = JBColor(0xD7D9E0, 0x35363A)
    private fun borderColor(): Color = JBColor(0xE4E5E9, 0x333437)
    private fun inputBackgroundColor(): Color = JBColor(0xFFFFFF, 0x2C2F34)
    private fun inputForegroundColor(): Color = JBColor(0x1C1C1E, 0xE8E8EB)
    private fun inputBorderColor(): Color = JBColor(0xDADBDE, 0x3D3F45)
    private fun primaryTextColor(): Color = JBColor(0x1C1C1E, 0xFFFFFF)
    private fun secondaryTextColor(): Color = JBColor(0x8E8E93, 0xA1A1AA)
    private fun errorColor(): Color = JBColor(0xD92D20, 0xF97066)
    private fun accentColor(): Color = JBColor(0x007AFF, 0x0A84FF)

    private fun insertSelectedCodeIntoInput() {
        val snippet = activeCodeContext.selectedCode
        if (snippet.isBlank()) {
            return
        }
        if (inputField.text.contains(snippet)) {
            return
        }
        val content = buildString {
            append(CODE_PROMPT_PREFIX)
            append("\n\n")
            append(snippet)
            if (!snippet.endsWith("\n")) {
                append("\n")
            }
        }
        inputField.text = content
        inputField.caretPosition = inputField.document.length
    }

    private fun determineContextForInput(input: String): CodeContext? {
        val snippet = activeCodeContext.selectedCode
        if (snippet.isBlank()) {
            return null
        }
        return if (input.contains(snippet)) activeCodeContext else null
    }

    private fun resolveActiveCodeContext(): CodeContext {
        if (codeContext.selectedCode.isNotBlank()) {
            return codeContext
        }

        val snapshot = session.lastCodeSnapshot ?: return codeContext
        val selectedRange = runCatching { TextRange(snapshot.selectionStart, snapshot.selectionEnd) }
            .getOrElse { TextRange(0, 0) }

        return codeContext.copy(
            selectedCode = snapshot.selectedCode,
            selectedRange = selectedRange,
            className = if (codeContext.className.isNotBlank()) codeContext.className else snapshot.className,
            methodName = codeContext.methodName ?: snapshot.methodName
        )
    }

    private fun updateStatus(text: String?, isError: Boolean = false) {
        SwingUtilities.invokeLater {
            statusResetTimer?.stop()
            statusLabel.text = text.orEmpty()
            statusLabel.foreground = if (isError) errorColor() else secondaryTextColor()
        }
    }

    private fun updateStatusWithAutoClear(message: String) {
        SwingUtilities.invokeLater {
            statusLabel.text = message
            statusLabel.foreground = secondaryTextColor()
            statusResetTimer?.stop()
            statusResetTimer = Timer(2500) {
                statusLabel.text = ""
            }.apply {
                isRepeats = false
                start()
            }
        }
    }
}

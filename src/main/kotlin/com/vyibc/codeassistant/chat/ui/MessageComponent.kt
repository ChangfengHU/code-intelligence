package com.vyibc.codeassistant.chat.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.vyibc.codeassistant.chat.model.ChatMessage
import com.vyibc.codeassistant.chat.model.MessageType
import com.vyibc.codeassistant.chat.settings.CodeChatSettings
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants

class MessageComponent(private val message: ChatMessage) : JPanel() {

    private val showTimestamp = CodeChatSettings.getInstance().state.showTimestamp
    private val timeFormat = SimpleDateFormat("HH:mm")

    init {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(0, 48, 20, 48)

        val row = horizontalBox()
        val content = createMessageContent().apply {
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        }

        when (message.type) {
            MessageType.USER -> {
                content.alignmentX = Component.RIGHT_ALIGNMENT
                row.add(Box.createHorizontalGlue())
                row.add(content)
            }
            MessageType.ASSISTANT, MessageType.CODE_ANALYSIS -> {
                content.alignmentX = Component.LEFT_ALIGNMENT
                row.add(content)
                row.add(Box.createHorizontalGlue())
            }
            MessageType.SYSTEM -> {
                row.add(Box.createHorizontalGlue())
                row.add(content)
                row.add(Box.createHorizontalGlue())
            }
        }

        add(row)

        if (showTimestamp && message.type != MessageType.SYSTEM) {
            add(Box.createVerticalStrut(6))
            val timestampRow = horizontalBox()
            val timeLabel = JLabel(timeFormat.format(Date(message.timestamp))).apply {
                font = Font("SF Pro Text", Font.PLAIN, 11)
                foreground = JBColor(0x8E8E93, 0xAEAEB2)
            }
            when (message.type) {
                MessageType.USER -> {
                    timestampRow.add(Box.createHorizontalGlue())
                    timestampRow.add(timeLabel)
                }
                MessageType.ASSISTANT, MessageType.CODE_ANALYSIS -> {
                    timestampRow.add(timeLabel)
                    timestampRow.add(Box.createHorizontalGlue())
                }
                else -> {
                    timestampRow.add(Box.createHorizontalGlue())
                    timestampRow.add(timeLabel)
                    timestampRow.add(Box.createHorizontalGlue())
                }
            }
            add(timestampRow)
        }
    }

    private fun horizontalBox(): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
    }

    private fun createMessageContent(): JComponent = when (message.type) {
        MessageType.SYSTEM -> createSystemContent()
        else -> createBubbleContent()
    }

    private fun createBubbleContent(): JComponent {
        val bubbleColor = when (message.type) {
            MessageType.USER -> JBColor(0x0A84FF, 0x0A84FF)
            MessageType.ASSISTANT -> JBColor(0xF1F2F7, 0x34363D)
            MessageType.CODE_ANALYSIS -> JBColor(0xE4EDFF, 0x2B3A52)
            MessageType.SYSTEM -> JBColor(0xE5E5EA, 0x3A3A3C)
        }
        val textColor = if (message.type == MessageType.USER) Color.WHITE else primaryTextColor()

        val bubble = object : JPanel() {
            init {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                border = BorderFactory.createEmptyBorder(14, 18, 14, 18)
                maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
                alignmentX = if (message.type == MessageType.USER) Component.RIGHT_ALIGNMENT else Component.LEFT_ALIGNMENT
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = bubbleColor
                g2.fillRoundRect(0, 0, width, height, 20, 20)
                g2.dispose()
                super.paintComponent(g)
            }
        }

        val (textPart, codePart) = extractTextAndCodeSegments()

        if (textPart.isNotBlank()) {
            bubble.add(createTextComponent(textPart, textColor))
        }

        codePart?.let { code ->
            if (bubble.componentCount > 0) {
                bubble.add(Box.createVerticalStrut(12))
            }
            bubble.add(createCodeSnippetComponent(code))
        }

        return bubble
    }

    private fun extractTextAndCodeSegments(): Pair<String, String?> {
        val raw = message.content.replace("\r\n", "\n").trimEnd()

        message.codeContext?.selectedCode?.takeIf { it.isNotBlank() }?.let { snippet ->
            val normalizedSnippet = snippet.replace("\r\n", "\n")
            val idx = raw.indexOf(normalizedSnippet)
            val textWithoutCode = when {
                idx >= 0 -> (raw.substring(0, idx) + raw.substring(idx + normalizedSnippet.length)).trim()
                raw.endsWith(normalizedSnippet) -> raw.removeSuffix(normalizedSnippet).trim()
                else -> raw
            }
            val textPart = textWithoutCode.ifBlank {
                if (raw.startsWith(CodeChatDialog.CODE_PROMPT_PREFIX)) CodeChatDialog.CODE_PROMPT_PREFIX else raw
            }
            return textPart to normalizedSnippet.trimEnd()
        }

        CODE_FENCE_REGEX.find(raw)?.let { match ->
            val codeBody = match.groupValues[2].trim('\n')
            val textual = raw.replace(match.value, "").trim()
            return textual to codeBody
        }

        return raw to null
    }

    private fun createTextComponent(text: String, color: Color): JComponent {
        val area = JTextArea(text)
        area.isEditable = false
        area.wrapStyleWord = true
        area.lineWrap = true
        area.font = Font("SF Pro Text", Font.PLAIN, 14)
        area.foreground = color
        area.background = Color(0, 0, 0, 0)
        area.border = BorderFactory.createEmptyBorder()
        area.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        area.alignmentX = Component.LEFT_ALIGNMENT
        return area
    }

    private fun createCodeSnippetComponent(code: String): JComponent {
        val normalized = code.trimEnd()
        val font = Font("JetBrains Mono", Font.PLAIN, 13)
        val area = JBTextArea(normalized).apply {
            isEditable = false
            lineWrap = false
            wrapStyleWord = false
            this.font = font
            foreground = primaryTextColor()
            background = codeBlockBackground()
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        }
        val scroll = JBScrollPane(
            area,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        ).apply {
            border = BorderFactory.createLineBorder(codeBlockBorderColor(), 1, true)
            background = codeBlockBackground()
            viewport.background = codeBlockBackground()
        }
        val metrics = area.getFontMetrics(font)
        val lineCount = normalized.ifEmpty { " " }.lines().size
        val preferredHeight = (metrics.height * lineCount + 24).coerceIn(metrics.height * 4, metrics.height * 14)
        scroll.preferredSize = Dimension(0, preferredHeight)
        scroll.maximumSize = Dimension(Int.MAX_VALUE, preferredHeight)
        scroll.alignmentX = Component.LEFT_ALIGNMENT
        return scroll
    }

    private fun createSystemContent(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.isOpaque = false
        panel.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

        val label = JLabel("<html><div style='text-align:center;'>${message.content.replace("\n", "<br>")}</div></html>")
        label.font = Font("SF Pro Text", Font.PLAIN, 12)
        label.foreground = secondaryTextColor()
        label.alignmentX = Component.CENTER_ALIGNMENT

        panel.add(label)
        return panel
    }

    private fun codeBlockBackground(): Color = JBColor(0x1F2333, 0x23262E)
    private fun codeBlockBorderColor(): Color = JBColor(0x2A3148, 0x3A3F4C)
    private fun primaryTextColor(): Color = JBColor(0x1C1C1E, 0xFFFFFF)
    private fun secondaryTextColor(): Color = JBColor(0x8E8E93, 0xA1A1AA)

    companion object {
        private val CODE_FENCE_REGEX = Regex("```(\\w+)?\\s*([\\s\\S]+?)```", RegexOption.MULTILINE)
    }
}

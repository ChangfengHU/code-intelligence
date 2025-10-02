package com.vyibc.codeassistant.chat.ui

import com.intellij.ui.JBColor
import com.vyibc.codeassistant.chat.model.ChatMessage
import com.vyibc.codeassistant.chat.model.MessageType
import com.vyibc.codeassistant.chat.settings.CodeChatSettings
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingConstants

class MessageComponent(private val message: ChatMessage) : JPanel(BorderLayout()) {

    private val showTimestamp = CodeChatSettings.getInstance().state.showTimestamp
    private val timeFormat = SimpleDateFormat("HH:mm")

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(0, leftInset(), 12, rightInset())
        val alignment = when (message.type) {
            MessageType.USER -> FlowLayout.RIGHT
            MessageType.ASSISTANT, MessageType.CODE_ANALYSIS -> FlowLayout.LEFT
            MessageType.SYSTEM -> FlowLayout.CENTER
        }
        val wrapper = JPanel(FlowLayout(alignment, 0, 0))
        wrapper.isOpaque = false
        wrapper.add(createContent())
        add(wrapper, BorderLayout.CENTER)
    }

    private fun createContent(): JComponent {
        return when (message.type) {
            MessageType.SYSTEM -> createSystemMessage()
            MessageType.USER -> createBubble(
                message.content,
                JBColor(0x007AFF, 0x0A84FF),
                Color.WHITE,
                SwingConstants.RIGHT
            )
            MessageType.ASSISTANT -> createBubble(
                message.content,
                JBColor(0xE5E5EA, 0x3A3A3C),
                primaryTextColor(),
                SwingConstants.LEFT
            )
            MessageType.CODE_ANALYSIS -> createBubble(
                message.content,
                JBColor(0xD1E3FF, 0x1C293A),
                primaryTextColor(),
                SwingConstants.LEFT
            )
        }
    }

    private fun createBubble(text: String, bubbleColor: Color, textColor: Color, timestampAlignment: Int): JComponent {
        val column = JPanel()
        column.layout = javax.swing.BoxLayout(column, javax.swing.BoxLayout.Y_AXIS)
        column.isOpaque = false

        val bubble = object : JPanel(BorderLayout()) {
            init {
                isOpaque = false
                border = BorderFactory.createEmptyBorder(10, 14, 10, 14)
                maximumSize = Dimension(560, Int.MAX_VALUE)
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = bubbleColor
                g2.fillRoundRect(0, 0, width, height, 22, 22)
                g2.dispose()
                super.paintComponent(g)
            }
        }

        val textArea = JTextArea(text)
        textArea.isEditable = false
        textArea.wrapStyleWord = true
        textArea.lineWrap = true
        textArea.font = Font("SF Pro Text", Font.PLAIN, 13)
        textArea.foreground = textColor
        textArea.background = Color(0, 0, 0, 0)
        textArea.border = BorderFactory.createEmptyBorder()
        textArea.maximumSize = Dimension(520, Int.MAX_VALUE)

        bubble.add(textArea, BorderLayout.CENTER)
        column.add(bubble)

        if (showTimestamp) {
            val timeLabel = JLabel(timeFormat.format(Date(message.timestamp)))
            timeLabel.font = Font("SF Pro Text", Font.PLAIN, 11)
            timeLabel.foreground = JBColor(0x8E8E93, 0xAEAEB2)
            timeLabel.border = BorderFactory.createEmptyBorder(6, 8, 0, 8)
            timeLabel.horizontalAlignment = timestampAlignment
            val timeWrapper = JPanel(BorderLayout())
            timeWrapper.isOpaque = false
            timeWrapper.maximumSize = Dimension(560, Int.MAX_VALUE)
            if (timestampAlignment == SwingConstants.RIGHT) {
                timeWrapper.add(timeLabel, BorderLayout.EAST)
            } else if (timestampAlignment == SwingConstants.LEFT) {
                timeWrapper.add(timeLabel, BorderLayout.WEST)
            } else {
                timeWrapper.add(timeLabel, BorderLayout.CENTER)
            }
            column.add(timeWrapper)
        }

        return column
    }

    private fun createSystemMessage(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.isOpaque = false
        panel.border = BorderFactory.createEmptyBorder(6, 24, 6, 24)

        val htmlContent = message.content.replace("\n", "<br>")
        val label = JLabel("<html><div style='text-align:center;'>$htmlContent</div></html>")
        label.font = Font("SF Pro Text", Font.PLAIN, 12)
        label.foreground = secondaryTextColor()
        label.horizontalAlignment = SwingConstants.CENTER
        panel.add(label, BorderLayout.CENTER)
        return panel
    }

    private fun primaryTextColor(): Color = JBColor(0x1C1C1E, 0xFFFFFF)
    private fun secondaryTextColor(): Color = JBColor(0x8E8E93, 0x8E8E93)

    private fun leftInset(): Int = when (message.type) {
        MessageType.USER -> 48
        MessageType.ASSISTANT, MessageType.CODE_ANALYSIS -> 12
        MessageType.SYSTEM -> 0
    }

    private fun rightInset(): Int = when (message.type) {
        MessageType.USER -> 12
        MessageType.ASSISTANT, MessageType.CODE_ANALYSIS -> 48
        MessageType.SYSTEM -> 0
    }
}

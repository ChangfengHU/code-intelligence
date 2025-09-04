package com.vyibc.codeassistant.chat.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextArea
import com.vyibc.codeassistant.chat.model.ChatMessage
import com.vyibc.codeassistant.chat.model.MessageType
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

/**
 * 消息显示组件
 */
class MessageComponent(private val message: ChatMessage) : JPanel(BorderLayout()) {
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss")
    
    init {
        createUI()
    }
    
    private fun createUI() {
        // 简化UI创建，确保能正确显示
        val (icon, senderName, bgColor) = getMessageStyle(message.type)
        
        // 卡片式消息容器
        val mainPanel = JPanel(BorderLayout())
        mainPanel.isOpaque = true
        mainPanel.background = JBColor.namedColor("Panel.background", Color(246,246,246))
        mainPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(6, 6, 6, 6),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.namedColor("Component.borderColor", Color(210,210,210)), 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
            )
        )
        
        // 创建头部
        val headerPanel = JPanel(BorderLayout())
        headerPanel.background = bgColor
        
        val headerLabel = JLabel("$icon $senderName")
        headerLabel.font = Font(Font.SANS_SERIF, Font.BOLD, 12)
        headerLabel.foreground = JBColor.namedColor("Label.foreground", Color(60,60,60))
        
        val timeLabel = JLabel(dateFormat.format(Date(message.timestamp)))
        timeLabel.font = Font(Font.SANS_SERIF, Font.PLAIN, 10)
        timeLabel.foreground = Color.GRAY
        timeLabel.background = bgColor
        
        headerPanel.add(headerLabel, BorderLayout.WEST)
        headerPanel.add(timeLabel, BorderLayout.EAST)
        
        // 创建内容区域
        val contentLabel = JLabel("<html><div style='width: 680px; padding: 6px 2px;'>${message.content.replace("\n", "<br>")}</div></html>")
        contentLabel.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        contentLabel.background = bgColor
        contentLabel.foreground = JBColor.namedColor("Label.foreground", Color(60,60,60))
        contentLabel.verticalAlignment = SwingConstants.TOP
        
        // 组装
        mainPanel.add(headerPanel, BorderLayout.NORTH)
        mainPanel.add(contentLabel, BorderLayout.CENTER)
        
        // 设置大小
        val lines = message.content.split('\n').size
        val estimatedHeight = Math.max(120, lines * 18 + 46)
        mainPanel.maximumSize = java.awt.Dimension(Int.MAX_VALUE, estimatedHeight)
        mainPanel.preferredSize = java.awt.Dimension(0, estimatedHeight)
        mainPanel.minimumSize = java.awt.Dimension(0, estimatedHeight)

        // 添加到当前组件
        layout = BorderLayout()
        add(mainPanel, BorderLayout.NORTH)

        // 当前组件自适应宽度，仅限制高度
        maximumSize = java.awt.Dimension(Int.MAX_VALUE, estimatedHeight)
        preferredSize = java.awt.Dimension(0, estimatedHeight)
        minimumSize = java.awt.Dimension(0, estimatedHeight)
        alignmentX = 0.0f
        isOpaque = false
        background = JBColor.background()
    }
    
    private fun getMessageStyle(type: MessageType): Triple<String, String, Color> {
        fun themed(light: Color, dark: Color) = if (JBColor.isBright()) light else dark
        return when (type) {
            MessageType.USER -> Triple("👤", "你", themed(Color(235,245,255), Color(36,46,66)))
            MessageType.ASSISTANT -> Triple("🤖", "助手", themed(Color(240,255,240), Color(36,56,46)))
            MessageType.CODE_ANALYSIS -> Triple("🔍", "代码分析", themed(Color(255,248,220), Color(66,56,36)))
            MessageType.SYSTEM -> Triple("ℹ️", "系统", JBColor.background())
        }
    }
    
    private fun createCodePanel(code: String): JPanel {
        val panel = JPanel(BorderLayout())
        
        val titleLabel = JLabel("📝 相关代码:")
        titleLabel.font = Font(Font.SANS_SERIF, Font.BOLD, 11)
        titleLabel.border = BorderFactory.createEmptyBorder(8, 0, 4, 0)
        
        val codeArea = JBTextArea(code)
        codeArea.isEditable = false
        codeArea.font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        codeArea.background = Color(250, 250, 250)
        codeArea.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        
        val scrollPane = JScrollPane(codeArea)
        scrollPane.preferredSize = java.awt.Dimension(0, Math.min(150, codeArea.preferredSize.height))
        scrollPane.border = BorderFactory.createLineBorder(Color.LIGHT_GRAY)
        
        panel.add(titleLabel, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
}
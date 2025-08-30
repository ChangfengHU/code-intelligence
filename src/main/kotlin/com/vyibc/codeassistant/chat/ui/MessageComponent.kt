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
        val headerPanel = JPanel(BorderLayout())
        val contentPanel = JPanel(BorderLayout())
        
        // 创建消息头部
        val (icon, senderName, bgColor) = getMessageStyle(message.type)
        
        val headerLabel = JLabel("$icon $senderName")
        headerLabel.font = Font(Font.SANS_SERIF, Font.BOLD, 12)
        
        val timeLabel = JLabel(dateFormat.format(Date(message.timestamp)))
        timeLabel.font = Font(Font.SANS_SERIF, Font.PLAIN, 10)
        timeLabel.foreground = Color.GRAY
        
        headerPanel.add(headerLabel, BorderLayout.WEST)
        headerPanel.add(timeLabel, BorderLayout.EAST)
        
        // 创建消息内容
        val contentArea = JBTextArea(message.content)
        contentArea.isEditable = false
        contentArea.lineWrap = true
        contentArea.wrapStyleWord = true
        contentArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        contentArea.background = bgColor
        contentArea.foreground = Color.BLACK
        
        // 设置内边距
        contentArea.border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
        
        // 确保组件有可见的大小
        val lines = message.content.split('\n').size
        val estimatedHeight = Math.max(40, lines * 20 + 16) // 每行约20px高度
        contentArea.preferredSize = java.awt.Dimension(600, estimatedHeight)
        
        contentPanel.add(contentArea, BorderLayout.CENTER)
        
        // 如果有代码上下文，显示代码片段
        if (message.codeContext != null && message.codeContext.selectedCode.isNotEmpty()) {
            val codePanel = createCodePanel(message.codeContext.selectedCode)
            contentPanel.add(codePanel, BorderLayout.SOUTH)
        }
        
        // 组装界面
        add(headerPanel, BorderLayout.NORTH)
        add(contentPanel, BorderLayout.CENTER)
        
        // 设置整体样式
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(4, 0, 4, 0),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1, true),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
            )
        )
        background = bgColor
        
        // 设置组件的首选大小
        val totalHeight = Math.max(60, (message.content.split('\n').size * 20) + 40)
        preferredSize = java.awt.Dimension(700, totalHeight)
        maximumSize = java.awt.Dimension(Short.MAX_VALUE.toInt(), totalHeight)
    }
    
    private fun getMessageStyle(type: MessageType): Triple<String, String, Color> {
        return when (type) {
            MessageType.USER -> Triple("👤", "你", Color(235, 245, 255))
            MessageType.ASSISTANT -> Triple("🤖", "助手", Color(240, 255, 240))
            MessageType.CODE_ANALYSIS -> Triple("🔍", "代码分析", Color(255, 248, 220))
            MessageType.SYSTEM -> Triple("ℹ️", "系统", Color(245, 245, 245))
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
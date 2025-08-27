package com.vyibc.codeassistant.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.*

class TranslationResultDialog(
    project: Project?,
    private val originalText: String,
    private val translatedText: String
) : DialogWrapper(project) {

    private lateinit var originalTextArea: JBTextArea
    private lateinit var translatedTextArea: JBTextArea

    init {
        title = "翻译结果"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(600, 400)

        // 创建主面板
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        
        // 原文区域
        contentPanel.add(createTextSection("原文:", originalText) { originalTextArea = it })
        contentPanel.add(Box.createVerticalStrut(10))
        
        // 译文区域
        contentPanel.add(createTextSection("译文:", translatedText) { translatedTextArea = it })
        
        panel.add(contentPanel, BorderLayout.CENTER)
        return panel
    }

    private fun createTextSection(label: String, text: String, textAreaCallback: (JBTextArea) -> Unit): JComponent {
        val panel = JPanel(BorderLayout())
        
        // 标签
        val titleLabel = JLabel(label)
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        panel.add(titleLabel, BorderLayout.NORTH)
        
        // 文本区域
        val textArea = JBTextArea(text)
        textArea.isEditable = false
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        textArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        textArea.background = UIManager.getColor("Panel.background")
        textAreaCallback(textArea)
        
        // 滚动面板
        val scrollPane = JBScrollPane(textArea)
        scrollPane.preferredSize = Dimension(550, 150)
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        
        panel.add(scrollPane, BorderLayout.CENTER)
        return panel
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            createCopyOriginalAction(),
            createCopyTranslationAction(),
            okAction
        )
    }

    private fun createCopyOriginalAction(): Action {
        return object : AbstractAction("复制原文") {
            override fun actionPerformed(e: ActionEvent?) {
                copyToClipboard(originalText)
                Messages.showInfoMessage("原文已复制到剪贴板", "复制成功")
            }
        }
    }

    private fun createCopyTranslationAction(): Action {
        return object : AbstractAction("复制译文") {
            override fun actionPerformed(e: ActionEvent?) {
                copyToClipboard(translatedText)
                Messages.showInfoMessage("译文已复制到剪贴板", "复制成功")
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    }

    override fun getOKAction(): Action {
        val action = super.getOKAction()
        action.putValue(Action.NAME, "关闭")
        return action
    }
}
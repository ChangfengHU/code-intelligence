package com.vyibc.codeassistant.chat.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.vyibc.codeassistant.chat.model.ChatSession
import com.vyibc.codeassistant.chat.service.SessionManager
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

/**
 * 会话历史对话框
 */
class SessionHistoryDialog(
    private val project: Project,
    private val currentSession: ChatSession
) : DialogWrapper(project) {
    
    private lateinit var sessionList: JBList<ChatSession>
    private lateinit var sessionListModel: DefaultListModel<ChatSession>
    private val sessionManager = SessionManager.getInstance(project)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
    
    init {
        title = "会话历史"
        init()
        loadSessions()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(500, 400)
        
        // 创建会话列表
        sessionListModel = DefaultListModel()
        sessionList = JBList(sessionListModel)
        sessionList.cellRenderer = SessionListCellRenderer()
        
        // 双击打开会话
        sessionList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    openSelectedSession()
                }
            }
        })
        
        val scrollPane = JBScrollPane(sessionList)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // 创建信息面板
        val infoPanel = createInfoPanel()
        panel.add(infoPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun createInfoPanel(): JPanel {
        val panel = JPanel()
        panel.add(JLabel("双击会话名称可打开对话窗口"))
        return panel
    }
    
    private fun loadSessions() {
        val sessions = sessionManager.getAllSessions()
        sessionListModel.clear()
        sessions.forEach { session ->
            sessionListModel.addElement(session)
        }
        
        // 选中当前会话
        val currentIndex = sessions.indexOf(currentSession)
        if (currentIndex >= 0) {
            sessionList.selectedIndex = currentIndex
        }
    }
    
    private fun openSelectedSession() {
        val selectedSession = sessionList.selectedValue ?: return
        
        // 关闭当前历史对话框
        close(OK_EXIT_CODE)
        
        // 如果选择的不是当前会话，打开新的对话窗口
        if (selectedSession.id != currentSession.id) {
            SwingUtilities.invokeLater {
                // 这里需要传入代码上下文，暂时使用空的上下文
                // 实际使用中可能需要从会话中恢复上下文信息
                val emptyContext = com.vyibc.codeassistant.chat.model.CodeContext(
                    selectedCode = "",
                    selectedRange = com.intellij.openapi.util.TextRange.EMPTY_RANGE,
                    className = selectedSession.className,
                    methodName = null,
                    classContext = "",
                    imports = emptyList()
                )
                
                val dialog = CodeChatDialog(project, selectedSession, emptyContext, false)
                dialog.show()
            }
        }
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(
            createDeleteAction(),
            createCloseAction()
        )
    }
    
    private fun createDeleteAction(): Action {
        return object : AbstractAction("删除会话") {
            override fun actionPerformed(e: ActionEvent?) {
                val selectedSession = sessionList.selectedValue ?: return
                
                val result = JOptionPane.showConfirmDialog(
                    this@SessionHistoryDialog.contentPane,
                    "确定要删除会话 \"${selectedSession.getDisplayName()}\" 吗？\\n这将永久删除所有对话记录。",
                    "确认删除",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                )
                
                if (result == JOptionPane.YES_OPTION) {
                    sessionManager.deleteSession(selectedSession.id)
                    loadSessions() // 刷新列表
                }
            }
        }
    }
    
    private fun createCloseAction(): Action {
        return object : AbstractAction("关闭") {
            override fun actionPerformed(e: ActionEvent?) {
                close(OK_EXIT_CODE)
            }
        }
    }
    
    /**
     * 会话列表渲染器
     */
    private inner class SessionListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            
            if (value is ChatSession) {
                val displayText = buildString {
                    append("📁 ${value.getDisplayName()}")
                    append("  (${value.getMessageCount()}条消息)")
                    append("\\n")
                    append("   ${dateFormat.format(Date(value.lastActiveAt))}")
                    if (value.id == currentSession.id) {
                        append("  [当前会话]")
                    }
                }
                
                text = "<html>${displayText.replace("\\n", "<br>")}</html>"
            }
            
            return this
        }
    }
}

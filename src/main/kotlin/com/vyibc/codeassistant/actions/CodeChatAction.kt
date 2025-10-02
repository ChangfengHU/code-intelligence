package com.vyibc.codeassistant.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.vyibc.codeassistant.chat.service.PSIContextAnalyzer
import com.vyibc.codeassistant.chat.service.SessionManager
import com.vyibc.codeassistant.chat.settings.CodeChatSettings
import com.vyibc.codeassistant.chat.ui.CodeChatDialog

class CodeChatAction : AnAction("问答助手") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        
        val selectedText = getSelectedText(editor)
        if (selectedText.isNullOrBlank()) {
            // 没有选择也允许打开，做空上下文问答
        }
        
        // 获取选中范围
        val selectionModel = editor.selectionModel
        val selectionRange = TextRange(
            selectionModel.selectionStart, 
            selectionModel.selectionEnd
        )
        
        // 分析代码上下文
        val contextAnalyzer = PSIContextAnalyzer.getInstance()
        val codeContext = contextAnalyzer.analyzeContext(psiFile, selectionRange)

        val className = codeContext.className.ifBlank { deriveIdentifier(psiFile) }
        if (className.isBlank()) {
            Messages.showWarningDialog(
                "无法确定当前文件的标识符，请确认文件已保存", 
                "提示"
            )
            return
        }
        
        // 获取或创建会话
        val sessionManager = SessionManager.getInstance(project)
        val filePath = psiFile.virtualFile?.path ?: psiFile.name
        val session = sessionManager.getOrCreateSession(className, filePath)

        // 如果没有选择代码，构建空上下文（允许用户直接问答）
        val actualContext = if (selectedText.isNullOrBlank()) {
            codeContext.copy(
                selectedCode = "",
                selectedRange = TextRange(0, 0),
                classContext = limitFileContext(psiFile)
            )
        } else codeContext
        
        // 显示对话框
        ApplicationManager.getApplication().invokeLater {
            val dialog = CodeChatDialog(project, session, actualContext)
            dialog.show()
        }
    }
    
    override fun update(e: AnActionEvent) {
        // 始终展示入口，允许无选择打开弹窗
        e.presentation.isEnabledAndVisible = true
        e.presentation.isEnabled = e.project != null
    }
    
    private fun getSelectedText(editor: Editor): String? {
        val selectionModel = editor.selectionModel
        return if (selectionModel.hasSelection()) {
            selectionModel.selectedText
        } else {
            null
        }
    }
    
    private fun deriveIdentifier(psiFile: PsiFile): String {
        val virtualFile = psiFile.virtualFile
        if (virtualFile != null) {
            val nameWithoutExtension = virtualFile.nameWithoutExtension
            if (nameWithoutExtension.isNotBlank()) {
                return nameWithoutExtension
            }
            return virtualFile.path
        }
        val fileName = psiFile.name
        val fallback = fileName.substringBeforeLast('.', fileName)
        return fallback.ifBlank { fileName }
    }

    private fun limitFileContext(psiFile: PsiFile): String {
        val text = psiFile.text
        if (text.length <= 3000) {
            return text
        }
        return text.take(3000) + "\n... (文件内容过长，已截断)"
    }
}

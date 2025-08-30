package com.vyibc.codeassistant.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import com.vyibc.codeassistant.chat.service.PSIContextAnalyzer
import com.vyibc.codeassistant.chat.service.SessionManager
import com.vyibc.codeassistant.chat.ui.CodeChatDialog

class CodeChatAction : AnAction("问答助手") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        
        val selectedText = getSelectedText(editor)
        if (selectedText.isNullOrBlank()) {
            Messages.showWarningDialog("请先选择要分析的代码", "提示")
            return
        }
        
        // 获取选中范围
        val selectionModel = editor.selectionModel
        val selectionRange = TextRange(
            selectionModel.selectionStart, 
            selectionModel.selectionEnd
        )
        
        // 获取类名
        val className = getClassName(psiFile, selectionRange)
        if (className == null) {
            Messages.showWarningDialog(
                "无法确定当前文件的标识符，请查看控制台错误信息", 
                "提示"
            )
            return
        }
        
        // 分析代码上下文
        val contextAnalyzer = PSIContextAnalyzer.getInstance()
        val codeContext = contextAnalyzer.analyzeContext(psiFile, selectionRange)
        
        // 获取或创建会话
        val sessionManager = SessionManager.getInstance()
        val session = sessionManager.getOrCreateSession(className, psiFile.virtualFile.path)
        
        // 显示对话框
        ApplicationManager.getApplication().invokeLater {
            val dialog = CodeChatDialog(project, session, codeContext)
            dialog.show()
        }
    }
    
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = hasSelection
    }
    
    private fun getSelectedText(editor: Editor): String? {
        val selectionModel = editor.selectionModel
        return if (selectionModel.hasSelection()) {
            selectionModel.selectedText
        } else {
            null
        }
    }
    
    private fun getClassName(psiFile: PsiFile, selectionRange: TextRange): String? {
        // 对于Java/Kotlin文件，使用PSI查找类名
        if (psiFile.name.endsWith(".java") || psiFile.name.endsWith(".kt")) {
            val selectedElement = psiFile.findElementAt(selectionRange.startOffset)
            val psiClass = PsiTreeUtil.getParentOfType(selectedElement, PsiClass::class.java)
            return psiClass?.qualifiedName
        }
        
        // 对于其他文件类型，使用文件路径生成唯一标识
        val fileName = psiFile.name
        val extension = fileName.substringAfterLast('.', "")
        val baseName = fileName.substringBeforeLast('.')
        
        // 尝试从文件内容中提取类名或函数名（简单的正则匹配）
        val fileText = psiFile.text
        val className = when (extension) {
            "py" -> {
                // Python: class ClassName:
                Regex("class\\s+(\\w+)\\s*[:(]").find(fileText)?.groupValues?.get(1)
            }
            "js", "ts" -> {
                // JavaScript/TypeScript: class ClassName 或 function ClassName
                Regex("(class|function)\\s+(\\w+)").find(fileText)?.groupValues?.get(2)
            }
            "go" -> {
                // Go: type StructName struct 或 func FuncName
                val structMatch = Regex("type\\s+(\\w+)\\s+struct").find(fileText)
                val funcMatch = Regex("func\\s+(\\w+)").find(fileText)
                structMatch?.groupValues?.get(1) ?: funcMatch?.groupValues?.get(1)
            }
            "cpp", "c", "h", "hpp" -> {
                // C++: class ClassName 或 struct ClassName
                Regex("(class|struct)\\s+(\\w+)").find(fileText)?.groupValues?.get(2)
            }
            else -> null
        }
        
        return className ?: "$baseName($extension)"
    }
}
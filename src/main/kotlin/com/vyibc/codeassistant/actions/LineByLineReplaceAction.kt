package com.vyibc.codeassistant.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.vyibc.codeassistant.services.AICodeTranslationService

class LineByLineReplaceAction : AnAction("逐行插入") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = getSelectedText(editor) ?: return
        
        val aiTranslationService = AICodeTranslationService()
        
        try {
            val lines = selectedText.split("\n")
            val document = editor.document
            val selectionModel = editor.selectionModel
            val startOffset = selectionModel.selectionStart
            val endOffset = selectionModel.selectionEnd
            
            // 计算起始行号
            val startLine = document.getLineNumber(startOffset)
            
            WriteCommandAction.runWriteCommandAction(project) {
                var insertOffset = 0
                
                for ((index, line) in lines.withIndex()) {
                    if (line.trim().isNotEmpty()) {
                        // 使用AI翻译单行，保持格式
                        val translatedLine = try {
                            val singleLineTranslation = aiTranslationService.translateCode(line)
                            // 清理可能的格式问题，保持单行
                            singleLineTranslation.replace("\n", " ").trim()
                        } catch (ex: Exception) {
                            "翻译失败: ${ex.message}"
                        }
                        
                        // 计算当前行的结束位置
                        val currentLineEndOffset = if (startLine + index + insertOffset < document.lineCount - 1) {
                            document.getLineEndOffset(startLine + index + insertOffset)
                        } else {
                            document.textLength
                        }
                        
                        // 保持原始行的缩进
                        val originalIndent = getLineIndent(line)
                        val insertText = "\n$originalIndent// $translatedLine"
                        document.insertString(currentLineEndOffset, insertText)
                        insertOffset++
                    }
                }
                
                // 取消选择
                selectionModel.removeSelection()
            }
            
            Messages.showInfoMessage("逐行翻译完成！", "逐行插入")
            
        } catch (ex: Exception) {
            Messages.showErrorDialog("AI翻译失败: ${ex.message}", "错误")
        }
    }
    
    /**
     * 获取行的缩进
     */
    private fun getLineIndent(line: String): String {
        val trimmed = line.trimStart()
        return line.substring(0, line.length - trimmed.length)
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
}
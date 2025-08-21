package com.vyibc.codeassistant.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.vyibc.codeassistant.services.PhoneticService

class PhoneticInsertionAction : AnAction("音标插入") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = getSelectedText(editor) ?: return
        
        val phoneticService = PhoneticService()
        
        try {
            val lines = selectedText.split("\n")
            val document = editor.document
            val selectionModel = editor.selectionModel
            val startOffset = selectionModel.selectionStart
            
            // 计算起始行号
            val startLine = document.getLineNumber(startOffset)
            
            WriteCommandAction.runWriteCommandAction(project) {
                var currentLineNumber = startLine
                var insertOffset = 0
                
                for ((index, line) in lines.withIndex()) {
                    if (line.trim().isNotEmpty()) {
                        val phoneticText = generatePhoneticText(line.trim(), phoneticService)
                        
                        if (phoneticText.isNotEmpty()) {
                            // 计算当前行的结束位置
                            val currentLineEndOffset = if (currentLineNumber + index + insertOffset < document.lineCount - 1) {
                                document.getLineEndOffset(currentLineNumber + index + insertOffset)
                            } else {
                                document.textLength
                            }
                            
                            // 在行尾插入换行和音标内容
                            val insertText = "\n// $phoneticText"
                            document.insertString(currentLineEndOffset, insertText)
                            insertOffset++
                        }
                    }
                }
                
                // 取消选择
                selectionModel.removeSelection()
            }
            
            Messages.showInfoMessage("音标插入完成！", "音标插入")
            
        } catch (ex: Exception) {
            Messages.showErrorDialog("音标插入失败: ${ex.message}", "错误")
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
    
    /**
     * 为行中的英文单词生成音标注释
     */
    private fun generatePhoneticText(line: String, phoneticService: PhoneticService): String {
        val words = extractEnglishWords(line)
        if (words.isEmpty()) return ""
        
        val phoneticPairs = mutableListOf<String>()
        
        for (word in words) {
            val phonetic = phoneticService.getPhonetic(word)
            if (phonetic != null) {
                phoneticPairs.add("$word: $phonetic")
            }
        }
        
        return if (phoneticPairs.isNotEmpty()) {
            "音标: ${phoneticPairs.joinToString(", ")}"
        } else {
            ""
        }
    }
    
    /**
     * 从代码行中提取英文单词
     */
    private fun extractEnglishWords(line: String): List<String> {
        // 移除常见的代码符号，提取英文单词
        val cleanLine = line
            .replace(Regex("[{}()\\[\\];,=+\\-*/\"'`<>!?.:&|^%#@~\$]"), " ")
            .replace(Regex("\\d+"), " ") // 移除数字
        
        return cleanLine.split(Regex("\\s+"))
            .filter { it.matches(Regex("[a-zA-Z]+")) && it.length > 1 } // 只保留纯英文单词且长度>1
            .map { it.lowercase() }
            .distinct()
    }
}
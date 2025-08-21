package com.vyibc.codeassistant.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.vyibc.codeassistant.services.AIPhoneticService
import com.vyibc.codeassistant.services.ProgressService

class PronunciationAssistAction : AnAction("发音辅助") {
    
    private val aiPhoneticService by lazy { AIPhoneticService() }
    private val progressService by lazy { ProgressService() }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = getSelectedText(editor) ?: return
        
        // 在UI线程中预先获取所需的编辑器信息
        val document = editor.document
        val selectionModel = editor.selectionModel
        val startOffset = selectionModel.selectionStart
        val startLine = document.getLineNumber(startOffset)
        
        val stages = listOf(
            "分析代码文本",
            "生成AI谐音",
            "插入谐音辅助"
        )
        
        progressService.runInBackground(
            project = project,
            title = "AI发音辅助生成中...",
            stages = stages,
            task = { _, updateStage ->
                
                try {
                    // 阶段1: 分析代码文本
                    updateStage(0, "正在分析选中的代码文本...")
                    Thread.sleep(300)
                    
                    val lines = selectedText.split("\n")
                    
                    // 阶段2: 生成AI谐音
                    updateStage(1, "正在调用AI生成高保真谐音...")
                    
                    val results = mutableListOf<Pair<Int, String>>() // 行号和谐音辅助内容
                    
                    for ((index, line) in lines.withIndex()) {
                        if (line.trim().isNotEmpty()) {
                            val englishWords = extractEnglishWords(line.trim())
                            
                            if (englishWords.isNotEmpty()) {
                                val wordsText = englishWords.joinToString(" ")
                                val assistText = aiPhoneticService.generatePhoneticAssist(wordsText)
                                
                                if (assistText.isNotEmpty()) {
                                    results.add(Pair(startLine + index, assistText))
                                }
                            }
                        }
                    }
                    
                    // 阶段3: 插入谐音辅助 - 在EDT线程中执行
                    updateStage(2, "正在插入谐音辅助到编辑器...")
                    
                    javax.swing.SwingUtilities.invokeLater {
                        WriteCommandAction.runWriteCommandAction(project) {
                            var insertOffset = 0
                            
                            for ((lineIndex, assistText) in results) {
                                val lineEndOffset = if (lineIndex + insertOffset < document.lineCount - 1) {
                                    document.getLineEndOffset(lineIndex + insertOffset)
                                } else {
                                    document.textLength
                                }
                                
                                document.insertString(lineEndOffset, "\n$assistText")
                                insertOffset++
                            }
                            
                            // 取消选择
                            selectionModel.removeSelection()
                        }
                    }
                    
                    Thread.sleep(200)
                    
                    // 显示完成消息（在EDT线程中）
                    javax.swing.SwingUtilities.invokeLater {
                        Messages.showInfoMessage("AI发音辅助插入完成！", "发音辅助")
                    }
                    
                } catch (ex: Exception) {
                    javax.swing.SwingUtilities.invokeLater {
                        Messages.showErrorDialog("AI发音辅助生成失败: ${ex.message}", "错误")
                    }
                }
            }
        )
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
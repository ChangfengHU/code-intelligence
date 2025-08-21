package com.vyibc.codeassistant.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.vyibc.codeassistant.services.AICodeTranslationService
import com.vyibc.codeassistant.services.CodeStructureBatchProcessor
import com.vyibc.codeassistant.services.ProgressService
import kotlin.math.ceil

class CodeExplanationAction : AnAction("代码解释") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = getSelectedText(editor) ?: return
        
        val progressService = ProgressService()
        val aiService = AICodeTranslationService()
        
        val lines = selectedText.split("\n")
        val lineCount = lines.size
        
        if (lineCount <= 200) {
            // 简单处理：直接翻译
            handleSimpleTranslation(project, editor, selectedText, progressService, aiService)
        } else {
            // 检查是否包含多个类定义
            val classCount = countClasses(selectedText)
            if (classCount > 1) {
                // 按类分批处理
                handleClassBasedTranslation(project, editor, selectedText, progressService, aiService, lines, classCount)
            } else {
                // 仍然直接处理（大文件单类）
                handleSimpleTranslation(project, editor, selectedText, progressService, aiService)
            }
        }
    }
    
    /**
     * 简单直接翻译处理
     */
    private fun handleSimpleTranslation(
        project: Project,
        editor: Editor,
        selectedText: String,
        progressService: ProgressService,
        aiService: AICodeTranslationService
    ) {
        val stages = listOf("准备代码解释", "AI翻译处理中", "完成代码解释")
        
        progressService.runInBackground(
            project = project,
            title = "代码解释处理中...",
            stages = stages,
            task = { _, updateStage ->
                
                updateStage(0, "准备代码解释...")
                Thread.sleep(300)
                
                updateStage(1, "AI翻译处理中 (${selectedText.split("\n").size} 行)...")
                
                try {
                    val translatedCode = aiService.translateCode(selectedText)
                    
                    // 直接替换选中内容
                    javax.swing.SwingUtilities.invokeLater {
                        WriteCommandAction.runWriteCommandAction(project) {
                            val selectionModel = editor.selectionModel
                            val startOffset = selectionModel.selectionStart
                            val endOffset = selectionModel.selectionEnd
                            val document = editor.document
                            
                            if (startOffset < document.textLength && endOffset <= document.textLength) {
                                document.replaceString(startOffset, endOffset, translatedCode)
                                selectionModel.setSelection(startOffset, startOffset + translatedCode.length)
                            }
                        }
                    }
                    
                    updateStage(2, "代码解释完成！")
                    Thread.sleep(200)
                    
                    translatedCode // 返回翻译结果
                    
                } catch (e: Exception) {
                    throw Exception("AI翻译失败: ${e.message}")
                }
            },
            onSuccess = { _ ->
                Messages.showInfoMessage("代码解释完成！", "代码解释")
            },
            onError = { ex ->
                Messages.showErrorDialog("代码解释失败: ${ex.message}", "错误")
            }
        )
    }
    
    /**
     * 基于类的分批翻译处理
     */
    private fun handleClassBasedTranslation(
        project: Project,
        editor: Editor,
        selectedText: String,
        progressService: ProgressService,
        aiService: AICodeTranslationService,
        lines: List<String>,
        classCount: Int
    ) {
        val classBatches = extractClassBatches(lines)
        
        val stages = mutableListOf("准备类级别处理")
        for ((index, batch) in classBatches.withIndex()) {
            stages.add("处理第 ${index + 1}/$classCount 个类")
        }
        stages.add("完成所有类处理")
        
        progressService.runInBackground(
            project = project,
            title = "代码解释 - 类级别处理中...",
            stages = stages,
            task = { _, updateStage ->
                
                updateStage(0, "准备类级别处理 (共 ${lines.size} 行，$classCount 个类)...")
                Thread.sleep(500)
                
                val translatedLines = mutableListOf<String>()
                
                for ((batchIndex, batch) in classBatches.withIndex()) {
                    val stageIndex = batchIndex + 1
                    val batchText = batch.joinToString("\n")
                    
                    updateStage(stageIndex, "正在处理第 ${batchIndex + 1}/$classCount 个类 (${batch.size} 行)...")
                    
                    try {
                        val translatedBatch = aiService.translateCode(batchText)
                        val translatedBatchLines = translatedBatch.split("\n")
                        translatedLines.addAll(translatedBatchLines)
                        
                        Thread.sleep(800) // 类间延迟
                        
                    } catch (e: Exception) {
                        println("类 ${batchIndex + 1} 处理失败: ${e.message}")
                        translatedLines.addAll(batch) // 保持原文
                    }
                }
                
                // 最终替换
                javax.swing.SwingUtilities.invokeLater {
                    WriteCommandAction.runWriteCommandAction(project) {
                        val selectionModel = editor.selectionModel
                        val startOffset = selectionModel.selectionStart
                        val endOffset = selectionModel.selectionEnd
                        val document = editor.document
                        val finalTranslatedText = translatedLines.joinToString("\n")
                        
                        if (startOffset < document.textLength && endOffset <= document.textLength) {
                            document.replaceString(startOffset, endOffset, finalTranslatedText)
                            selectionModel.setSelection(startOffset, startOffset + finalTranslatedText.length)
                        }
                    }
                }
                
                updateStage(stages.size - 1, "所有类处理完成！")
                Thread.sleep(200)
                
                translatedLines.joinToString("\n")
            },
            onSuccess = { _ ->
                Messages.showInfoMessage(
                    "代码解释完成！\n共处理 ${lines.size} 行代码，包含 $classCount 个类。", 
                    "类级别代码解释"
                )
            },
            onError = { ex ->
                Messages.showErrorDialog("代码解释过程中出现错误: ${ex.message}", "错误")
            }
        )
    }
    
    /**
     * 计算代码中的类定义数量
     */
    private fun countClasses(code: String): Int {
        val classPatterns = listOf(
            Regex("^\\s*class\\s+\\w+", RegexOption.MULTILINE),           // Java/Kotlin/Python/C#类
            Regex("^\\s*public\\s+class\\s+\\w+", RegexOption.MULTILINE), // Java public类
            Regex("^\\s*interface\\s+\\w+", RegexOption.MULTILINE),       // 接口
            Regex("^\\s*enum\\s+\\w+", RegexOption.MULTILINE),            // 枚举
            Regex("^\\s*data\\s+class\\s+\\w+", RegexOption.MULTILINE),   // Kotlin data类
            Regex("^\\s*object\\s+\\w+", RegexOption.MULTILINE),          // Kotlin object
            Regex("^\\s*abstract\\s+class\\s+\\w+", RegexOption.MULTILINE) // 抽象类
        )
        
        return classPatterns.sumOf { pattern ->
            pattern.findAll(code).count()
        }
    }
    
    /**
     * 按类定义提取批次
     */
    private fun extractClassBatches(lines: List<String>): List<List<String>> {
        val batches = mutableListOf<List<String>>()
        var currentBatch = mutableListOf<String>()
        var braceCount = 0
        var inClass = false
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            // 检查是否是类定义的开始
            if (isClassDefinitionStart(trimmedLine)) {
                // 如果已经有内容，先保存当前批次
                if (currentBatch.isNotEmpty() && inClass) {
                    batches.add(currentBatch.toList())
                    currentBatch.clear()
                    braceCount = 0
                }
                inClass = true
            }
            
            currentBatch.add(line)
            
            if (inClass) {
                // 计算大括号平衡
                braceCount += line.count { it == '{' }
                braceCount -= line.count { it == '}' }
                
                // 如果大括号平衡且不在类中，结束当前批次
                if (braceCount <= 0 && currentBatch.size > 1) {
                    batches.add(currentBatch.toList())
                    currentBatch.clear()
                    braceCount = 0
                    inClass = false
                }
            }
        }
        
        // 添加剩余内容
        if (currentBatch.isNotEmpty()) {
            batches.add(currentBatch.toList())
        }
        
        // 如果没有找到类，返回整个文件作为一个批次
        return if (batches.isEmpty()) {
            listOf(lines)
        } else {
            batches
        }
    }
    
    /**
     * 判断是否是类定义开始
     */
    private fun isClassDefinitionStart(line: String): Boolean {
        val classKeywords = listOf(
            "class ", "public class ", "private class ", "protected class ",
            "abstract class ", "final class ", "data class ", "sealed class ",
            "interface ", "enum ", "object "
        )
        
        return classKeywords.any { keyword ->
            line.contains(keyword) && (line.contains("{") || !line.endsWith(";"))
        }
    }
    
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = hasSelection
        
        // 简化菜单文本提示
        if (hasSelection && editor != null) {
            val selectedText = getSelectedText(editor)
            val lineCount = selectedText?.split("\n")?.size ?: 0
            
            e.presentation.text = when {
                lineCount <= 200 -> "代码解释 ($lineCount 行)"
                else -> {
                    val classCount = countClasses(selectedText ?: "")
                    if (classCount > 1) {
                        "代码解释 ($lineCount 行，$classCount 个类)"
                    } else {
                        "代码解释 ($lineCount 行)"
                    }
                }
            }
        } else {
            e.presentation.text = "代码解释"
        }
    }
    
    private fun getSelectedText(editor: Editor?): String? {
        if (editor == null) return null
        val selectionModel = editor.selectionModel
        return if (selectionModel.hasSelection()) {
            selectionModel.selectedText
        } else {
            null
        }
    }
}
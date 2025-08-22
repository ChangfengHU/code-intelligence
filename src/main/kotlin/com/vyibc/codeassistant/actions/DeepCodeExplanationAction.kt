package com.vyibc.codeassistant.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.vyibc.codeassistant.services.AIDeepAnalysisService
import com.vyibc.codeassistant.services.ProgressService

class DeepCodeExplanationAction : AnAction("深度解释") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = getSelectedText(editor) ?: return
        
        val progressService = ProgressService()
        val aiService = AIDeepAnalysisService()
        
        val lines = selectedText.split("\n")
        val lineCount = lines.size
        
        if (lineCount <= 200) {
            // 简单处理：直接深度分析
            handleSimpleDeepAnalysis(project, editor, selectedText, progressService, aiService)
        } else {
            // 检查是否包含多个类定义
            val classCount = countClasses(selectedText)
            if (classCount > 1) {
                // 按类分批处理
                handleClassBasedDeepAnalysis(project, editor, selectedText, progressService, aiService, lines, classCount)
            } else {
                // 仍然直接处理（大文件单类）
                handleSimpleDeepAnalysis(project, editor, selectedText, progressService, aiService)
            }
        }
    }
    
    /**
     * 简单直接深度分析处理
     */
    private fun handleSimpleDeepAnalysis(
        project: Project,
        editor: Editor,
        selectedText: String,
        progressService: ProgressService,
        aiService: AIDeepAnalysisService
    ) {
        val stages = listOf("准备深度解释", "AI深度分析中", "完成深度解释")
        
        progressService.runInBackground(
            project = project,
            title = "深度代码解释处理中...",
            stages = stages,
            task = { _, updateStage ->
                
                updateStage(0, "准备深度解释...")
                Thread.sleep(300)
                
                updateStage(1, "AI深度分析中 (${selectedText.split("\n").size} 行)...")
                
                try {
                    val deepAnalysisResult = aiService.deepAnalyzeCode(selectedText)
                    
                    // 直接替换选中内容
                    javax.swing.SwingUtilities.invokeLater {
                        WriteCommandAction.runWriteCommandAction(project) {
                            val selectionModel = editor.selectionModel
                            val startOffset = selectionModel.selectionStart
                            val endOffset = selectionModel.selectionEnd
                            val document = editor.document
                            
                            if (startOffset < document.textLength && endOffset <= document.textLength) {
                                document.replaceString(startOffset, endOffset, deepAnalysisResult)
                                selectionModel.setSelection(startOffset, startOffset + deepAnalysisResult.length)
                            }
                        }
                    }
                    
                    updateStage(2, "深度解释完成！")
                    Thread.sleep(200)
                    
                    deepAnalysisResult // 返回分析结果
                    
                } catch (e: Exception) {
                    throw Exception("AI深度分析失败: ${e.message}")
                }
            },
            onSuccess = { _ ->
                Messages.showInfoMessage("深度解释完成！", "深度代码解释")
            },
            onError = { ex ->
                Messages.showErrorDialog("深度解释失败: ${ex.message}", "错误")
            }
        )
    }
    
    /**
     * 基于类的分批深度分析处理
     */
    private fun handleClassBasedDeepAnalysis(
        project: Project,
        editor: Editor,
        selectedText: String,
        progressService: ProgressService,
        aiService: AIDeepAnalysisService,
        lines: List<String>,
        classCount: Int
    ) {
        val classBatches = extractClassBatches(lines)
        
        val stages = mutableListOf("准备类级别深度处理")
        for ((index, batch) in classBatches.withIndex()) {
            stages.add("深度分析第 ${index + 1}/$classCount 个类")
        }
        stages.add("完成所有类深度处理")
        
        progressService.runInBackground(
            project = project,
            title = "深度代码解释 - 类级别处理中...",
            stages = stages,
            task = { _, updateStage ->
                
                updateStage(0, "准备类级别深度处理 (共 ${lines.size} 行，$classCount 个类)...")
                Thread.sleep(500)
                
                val analyzedLines = mutableListOf<String>()
                
                for ((batchIndex, batch) in classBatches.withIndex()) {
                    val stageIndex = batchIndex + 1
                    val batchText = batch.joinToString("\n")
                    
                    updateStage(stageIndex, "正在深度分析第 ${batchIndex + 1}/$classCount 个类 (${batch.size} 行)...")
                    
                    try {
                        val analyzedBatch = aiService.deepAnalyzeCode(batchText)
                        val analyzedBatchLines = analyzedBatch.split("\n")
                        analyzedLines.addAll(analyzedBatchLines)
                        
                        Thread.sleep(1200) // 深度分析需要更多时间
                        
                    } catch (e: Exception) {
                        println("类 ${batchIndex + 1} 深度分析失败: ${e.message}")
                        analyzedLines.addAll(batch) // 保持原文
                    }
                }
                
                // 最终替换
                javax.swing.SwingUtilities.invokeLater {
                    WriteCommandAction.runWriteCommandAction(project) {
                        val selectionModel = editor.selectionModel
                        val startOffset = selectionModel.selectionStart
                        val endOffset = selectionModel.selectionEnd
                        val document = editor.document
                        val finalAnalyzedText = analyzedLines.joinToString("\n")
                        
                        if (startOffset < document.textLength && endOffset <= document.textLength) {
                            document.replaceString(startOffset, endOffset, finalAnalyzedText)
                            selectionModel.setSelection(startOffset, startOffset + finalAnalyzedText.length)
                        }
                    }
                }
                
                updateStage(stages.size - 1, "所有类深度处理完成！")
                Thread.sleep(200)
                
                analyzedLines.joinToString("\n")
            },
            onSuccess = { _ ->
                Messages.showInfoMessage(
                    "深度解释完成！\n共处理 ${lines.size} 行代码，包含 $classCount 个类。", 
                    "类级别深度解释"
                )
            },
            onError = { ex ->
                Messages.showErrorDialog("深度解释过程中出现错误: ${ex.message}", "错误")
            }
        )
    }
    
    /**
     * 计算代码中的类定义数量 - 复用基础解释的逻辑
     */
    private fun countClasses(code: String): Int {
        val classPatterns = listOf(
            Regex("^\\s*class\\s+\\w+", RegexOption.MULTILINE),
            Regex("^\\s*public\\s+class\\s+\\w+", RegexOption.MULTILINE),
            Regex("^\\s*interface\\s+\\w+", RegexOption.MULTILINE),
            Regex("^\\s*enum\\s+\\w+", RegexOption.MULTILINE),
            Regex("^\\s*data\\s+class\\s+\\w+", RegexOption.MULTILINE),
            Regex("^\\s*object\\s+\\w+", RegexOption.MULTILINE),
            Regex("^\\s*abstract\\s+class\\s+\\w+", RegexOption.MULTILINE)
        )
        
        return classPatterns.sumOf { pattern ->
            pattern.findAll(code).count()
        }
    }
    
    /**
     * 按类定义提取批次 - 复用基础解释的逻辑
     */
    private fun extractClassBatches(lines: List<String>): List<List<String>> {
        val batches = mutableListOf<List<String>>()
        var currentBatch = mutableListOf<String>()
        var braceCount = 0
        var inClass = false
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            if (isClassDefinitionStart(trimmedLine)) {
                if (currentBatch.isNotEmpty() && inClass) {
                    batches.add(currentBatch.toList())
                    currentBatch.clear()
                    braceCount = 0
                }
                inClass = true
            }
            
            currentBatch.add(line)
            
            if (inClass) {
                braceCount += line.count { it == '{' }
                braceCount -= line.count { it == '}' }
                
                if (braceCount <= 0 && currentBatch.size > 1) {
                    batches.add(currentBatch.toList())
                    currentBatch.clear()
                    braceCount = 0
                    inClass = false
                }
            }
        }
        
        if (currentBatch.isNotEmpty()) {
            batches.add(currentBatch.toList())
        }
        
        return if (batches.isEmpty()) {
            listOf(lines)
        } else {
            batches
        }
    }
    
    /**
     * 判断是否是类定义开始 - 复用基础解释的逻辑
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
        
        // 深度解释菜单文本提示
        if (hasSelection && editor != null) {
            val selectedText = getSelectedText(editor)
            val lineCount = selectedText?.split("\n")?.size ?: 0
            
            e.presentation.text = when {
                lineCount <= 200 -> "深度解释 ($lineCount 行)"
                else -> {
                    val classCount = countClasses(selectedText ?: "")
                    if (classCount > 1) {
                        "深度解释 ($lineCount 行，$classCount 个类)"
                    } else {
                        "深度解释 ($lineCount 行)"
                    }
                }
            }
        } else {
            e.presentation.text = "深度解释"
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
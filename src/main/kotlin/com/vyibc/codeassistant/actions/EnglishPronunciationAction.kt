package com.vyibc.codeassistant.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.vyibc.codeassistant.services.TextToSpeechService

class EnglishPronunciationAction : AnAction("英语发音") {
    
    // 延迟初始化TTS服务
    private val ttsService by lazy { TextToSpeechService() }
    
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = getSelectedText(editor) ?: return
        
        try {
            // 处理多行文本，逐行发音（排队执行）
            val lines = selectedText.split("\n").filter { it.trim().isNotEmpty() }
            
            if (lines.isEmpty()) {
                Messages.showInfoMessage("没有找到可发音的文本内容", "提示")
                return
            }
            
            // 清空之前的队列
            ttsService.stopSpeaking()
            
            // 将所有行添加到发音队列
            for (line in lines) {
                val cleanText = cleanTextForSpeech(line.trim())
                if (cleanText.isNotEmpty()) {
                    ttsService.speakEnglish(cleanText)
                }
            }
            
            // 显示提示信息
            if (lines.size == 1) {
                Messages.showInfoMessage("开始发音", "英语发音")
            } else {
                Messages.showInfoMessage("已添加 ${lines.size} 行到发音队列", "英语发音")
            }
            
        } catch (ex: Exception) {
            Messages.showErrorDialog("发音失败: ${ex.message}", "错误")
        }
    }
    
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = hasSelection
        
        // 如果正在发音，显示状态
        if (ttsService.isSpeaking()) {
            e.presentation.text = "英语发音 (播放中...)"
        } else if (ttsService.getQueueSize() > 0) {
            e.presentation.text = "英语发音 (队列: ${ttsService.getQueueSize()})"
        } else {
            e.presentation.text = "英语发音"
        }
    }
    
    private fun getSelectedText(editor: com.intellij.openapi.editor.Editor): String? {
        val selectionModel = editor.selectionModel
        return if (selectionModel.hasSelection()) {
            selectionModel.selectedText
        } else {
            null
        }
    }
    
    /**
     * 清理文本，移除代码符号，保留可发音的英文内容
     */
    private fun cleanTextForSpeech(text: String): String {
        return text
            .replace(Regex("[{}()\\[\\];,=+\\-*/\"'`]"), " ") // 移除常见代码符号
            .replace(Regex("\\s+"), " ") // 合并多个空格
            .replace(Regex("^//\\s*"), "") // 移除行注释开头
            .replace(Regex("^/\\*\\s*"), "") // 移除块注释开头
            .replace(Regex("\\s*\\*/$"), "") // 移除块注释结尾
            .replace(Regex("\\d+"), "") // 移除数字
            .replace(Regex("[<>{}\\[\\]().,;:!?\"']"), " ") // 移除更多符号
            .trim()
            .takeIf { it.any { char -> char.isLetter() } } ?: "" // 只保留包含字母的文本
    }
}
package com.vyibc.codeassistant.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.vyibc.codeassistant.services.GoogleTranslateService
import com.vyibc.codeassistant.services.ProgressService

class ChinesePronunciationAction : AnAction("中文发音") {
    
    private val translateService by lazy { GoogleTranslateService() }
    private val progressService by lazy { ProgressService() }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = getSelectedText(editor) ?: return
        
        val stages = listOf(
            "分析选中文本",
            "翻译成中文",
            "中文发音中"
        )
        
        progressService.runInBackground(
            project = project,
            title = "中文发音进行中...",
            stages = stages,
            task = { _, updateStage ->
                
                try {
                    // 阶段1: 分析选中文本
                    updateStage(0, "正在分析选中的英文文本...")
                    Thread.sleep(300)
                    
                    val lines = selectedText.split("\n").filter { it.trim().isNotEmpty() }
                    if (lines.isEmpty()) {
                        javax.swing.SwingUtilities.invokeLater {
                            Messages.showInfoMessage("没有找到可处理的文本内容", "提示")
                        }
                        return@runInBackground
                    }
                    
                    for ((index, line) in lines.withIndex()) {
                        val cleanEnglishText = cleanTextForTranslation(line.trim())
                        if (cleanEnglishText.isNotEmpty()) {
                            
                            // 阶段2: 翻译成中文
                            updateStage(1, "正在翻译: ${cleanEnglishText.take(20)}${if (cleanEnglishText.length > 20) "..." else ""}")
                            
                            val chineseTranslation = try {
                                translateService.translate(cleanEnglishText, "en", "zh")
                            } catch (e: Exception) {
                                "翻译失败"
                            }
                            
                            if (chineseTranslation.isNotEmpty() && chineseTranslation != "翻译失败") {
                                // 阶段3: 中文发音
                                updateStage(2, "正在发音中文: ${chineseTranslation.take(20)}${if (chineseTranslation.length > 20) "..." else ""}")
                                
                                // 使用系统中文TTS发音
                                speakChinese(chineseTranslation)
                                
                                // 等待中文发音完成
                                Thread.sleep(Math.max(2000L, chineseTranslation.length * 300L))
                            } else {
                                javax.swing.SwingUtilities.invokeLater {
                                    Messages.showWarningDialog("翻译失败，无法发音", "警告")
                                }
                            }
                            
                            // 如果有多行，稍作停顿再处理下一行
                            if (index < lines.size - 1) {
                                Thread.sleep(1000)
                            }
                        }
                    }
                    
                    // 显示完成消息
                    javax.swing.SwingUtilities.invokeLater {
                        if (lines.size == 1) {
                            Messages.showInfoMessage("中文发音完成", "中文发音")
                        } else {
                            Messages.showInfoMessage("已完成 ${lines.size} 行中文发音", "中文发音")
                        }
                    }
                    
                } catch (ex: Exception) {
                    javax.swing.SwingUtilities.invokeLater {
                        Messages.showErrorDialog("中文发音失败: ${ex.message}", "错误")
                    }
                }
            }
        )
    }
    
    /**
     * 中文TTS发音 - 针对不同操作系统优化
     */
    private fun speakChinese(text: String) {
        try {
            val osName = System.getProperty("os.name").lowercase()
            
            when {
                osName.contains("mac") -> {
                    // macOS 使用 say 命令，支持中文
                    val process = ProcessBuilder("say", "-v", "Ting-Ting", text)
                        .redirectErrorStream(true)
                        .start()
                    process.waitFor()
                }
                osName.contains("windows") -> {
                    // Windows 使用 PowerShell TTS，支持中文
                    val escapedText = text.replace("'", "''") // 转义单引号
                    val psScript = """
Add-Type -AssemblyName System.Speech
${'$'}synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
${'$'}synth.SelectVoiceByHints('Female', 'Adult', 0, [System.Globalization.CultureInfo]::CreateSpecificCulture('zh-CN'))
${'$'}synth.Speak('$escapedText')
${'$'}synth.Dispose()
                    """.trimIndent()
                    
                    val process = ProcessBuilder("powershell", "-Command", psScript)
                        .redirectErrorStream(true)
                        .start()
                    process.waitFor()
                }
                osName.contains("linux") -> {
                    // Linux 尝试使用不同的TTS引擎
                    val ttsCommands = listOf(
                        listOf("espeak", "-v", "zh", text),
                        listOf("festival", "--tts", text),
                        listOf("spd-say", "-l", "zh", text)
                    )
                    
                    var spoken = false
                    for (command in ttsCommands) {
                        if (isCommandAvailable(command[0])) {
                            val process = ProcessBuilder(command)
                                .redirectErrorStream(true)
                                .start()
                            process.waitFor()
                            spoken = true
                            break
                        }
                    }
                    
                    if (!spoken) {
                        println("Linux系统未找到支持中文的TTS引擎")
                    }
                }
                else -> {
                    println("不支持的操作系统: $osName")
                }
            }
        } catch (e: Exception) {
            println("中文TTS发音失败: ${e.message}")
        }
    }
    
    private fun isCommandAvailable(command: String): Boolean {
        return try {
            val process = ProcessBuilder("which", command).start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = hasSelection
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
     * 清理文本，保留可翻译的英文内容
     */
    private fun cleanTextForTranslation(text: String): String {
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
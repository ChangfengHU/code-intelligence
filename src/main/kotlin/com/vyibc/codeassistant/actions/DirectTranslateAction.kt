package com.vyibc.codeassistant.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.Messages
import com.vyibc.codeassistant.services.GoogleTranslateService
import com.vyibc.codeassistant.ui.TranslationResultDialog

class DirectTranslateAction : AnAction("直接翻译") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = getSelectedText(editor) ?: return
        
        val translationService = GoogleTranslateService()
        
        try {
            val translatedText = translationService.translate(selectedText)
            
            // 使用自定义对话框显示翻译结果
            val dialog = TranslationResultDialog(project, selectedText, translatedText)
            dialog.show()
            
        } catch (ex: Exception) {
            Messages.showErrorDialog("翻译失败: ${ex.message}", "错误")
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
}
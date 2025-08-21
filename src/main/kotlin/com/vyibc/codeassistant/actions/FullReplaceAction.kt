package com.vyibc.codeassistant.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.vyibc.codeassistant.services.GoogleTranslateService

class FullReplaceAction : AnAction("全文替换") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = getSelectedText(editor) ?: return
        
        val translationService = GoogleTranslateService()
        
        try {
            val translatedText = translationService.translate(selectedText)
            
            WriteCommandAction.runWriteCommandAction(project) {
                val selectionModel = editor.selectionModel
                val start = selectionModel.selectionStart
                val end = selectionModel.selectionEnd
                
                editor.document.replaceString(start, end, translatedText)
                selectionModel.setSelection(start, start + translatedText.length)
            }
            
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
package com.vyibc.codeassistant.services

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

class ProgressService {
    
    /**
     * 执行带进度提示的任务
     */
    fun <T> runWithProgress(
        project: Project?,
        title: String,
        stages: List<String>,
        task: (indicator: ProgressIndicator, updateStage: (Int, String) -> Unit) -> T
    ): T? {
        
        var result: T? = null
        
        ProgressManager.getInstance().run(object : Task.Modal(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                
                val updateStage = { stageIndex: Int, stageText: String ->
                    val progress = (stageIndex.toDouble() / stages.size)
                    indicator.fraction = progress
                    indicator.text = "第 ${stageIndex + 1}/${stages.size} 阶段"
                    indicator.text2 = stageText
                }
                
                result = task(indicator, updateStage)
            }
        })
        
        return result
    }
    
    /**
     * 执行后台任务（不阻塞UI）
     */
    fun <T> runInBackground(
        project: Project?,
        title: String,
        stages: List<String>,
        task: (indicator: ProgressIndicator, updateStage: (Int, String) -> Unit) -> T,
        onSuccess: (T) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = false
                    
                    val updateStage = { stageIndex: Int, stageText: String ->
                        val progress = (stageIndex.toDouble() / stages.size)
                        indicator.fraction = progress
                        indicator.text = "第 ${stageIndex + 1}/${stages.size} 阶段"
                        indicator.text2 = stageText
                    }
                    
                    val result = task(indicator, updateStage)
                    
                    // 在EDT线程中执行成功回调
                    javax.swing.SwingUtilities.invokeLater {
                        onSuccess(result)
                    }
                    
                } catch (e: Exception) {
                    // 在EDT线程中执行错误回调
                    javax.swing.SwingUtilities.invokeLater {
                        onError(e)
                    }
                }
            }
        })
    }
}
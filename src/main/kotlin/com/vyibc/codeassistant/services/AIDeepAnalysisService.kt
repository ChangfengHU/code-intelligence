package com.vyibc.codeassistant.services

import com.vyibc.codeassistant.config.AIProvider
import com.vyibc.codeassistant.services.ai.*
import com.vyibc.codeassistant.settings.CodeAssistantSettings
import java.io.IOException

class AIDeepAnalysisService {
    
    /**
     * 使用配置的AI服务进行代码深度分析
     * @param code 要分析的代码
     * @return 深度分析后的代码
     */
    fun deepAnalyzeCode(code: String): String {
        if (code.isBlank()) return code
        
        try {
            val aiService = createDeepAnalysisAIService()
            return aiService.deepAnalyzeCode(code)
        } catch (e: Exception) {
            throw IOException("代码深度分析失败: ${e.message}")
        }
    }
    
    private fun createDeepAnalysisAIService(): DeepAnalysisAIService {
        val settings = CodeAssistantSettings.getInstance()
        val config = settings.getAIConfig()
        
        return when (config.provider) {
            AIProvider.OPENAI -> OpenAIDeepAnalysisService(config)
            AIProvider.AZURE_OPENAI -> {
                // TODO: 实现Azure OpenAI深度分析服务
                throw IOException("Azure OpenAI深度分析服务暂未实现")
            }
            AIProvider.CLAUDE -> {
                // TODO: 实现Claude深度分析服务
                throw IOException("Claude深度分析服务暂未实现")
            }
            AIProvider.GEMINI -> {
                // TODO: 实现Gemini深度分析服务
                throw IOException("Gemini深度分析服务暂未实现")
            }
        }
    }
}
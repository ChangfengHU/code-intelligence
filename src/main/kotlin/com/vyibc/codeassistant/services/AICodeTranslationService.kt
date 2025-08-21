package com.vyibc.codeassistant.services

import com.vyibc.codeassistant.config.AIProvider
import com.vyibc.codeassistant.services.ai.*
import com.vyibc.codeassistant.settings.CodeAssistantSettings
import java.io.IOException

class AICodeTranslationService {
    
    /**
     * 使用配置的AI服务进行代码翻译
     * @param code 要翻译的代码
     * @return 翻译后的代码
     */
    fun translateCode(code: String): String {
        if (code.isBlank()) return code
        
        try {
            val aiService = createAIService()
            return aiService.translateCode(code)
        } catch (e: Exception) {
            throw IOException("代码翻译失败: ${e.message}")
        }
    }
    
    private fun createAIService(): AIService {
        val settings = CodeAssistantSettings.getInstance()
        val config = settings.getAIConfig()
        
        return when (config.provider) {
            AIProvider.OPENAI -> OpenAIService(config)
            AIProvider.AZURE_OPENAI -> {
                // TODO: 实现Azure OpenAI服务
                throw IOException("Azure OpenAI服务暂未实现")
            }
            AIProvider.CLAUDE -> {
                // TODO: 实现Claude服务
                throw IOException("Claude服务暂未实现")
            }
            AIProvider.GEMINI -> {
                // TODO: 实现Gemini服务
                throw IOException("Gemini服务暂未实现")
            }
        }
    }
}
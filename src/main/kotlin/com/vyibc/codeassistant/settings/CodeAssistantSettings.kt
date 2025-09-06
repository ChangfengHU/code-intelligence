package com.vyibc.codeassistant.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import com.vyibc.codeassistant.config.*

@State(
    name = "CodeAssistantSettings",
    storages = [Storage("CodeAssistant.xml")]
)
@Service
class CodeAssistantSettings : PersistentStateComponent<CodeAssistantSettings.State> {
    
    data class State(
        // TTS 配置
        var ttsProvider: String = TTSProvider.OPENAI.name,
        var openaiApiKey: String = "",
        var openaiVoice: String = OpenAIVoice.ALLOY.name,
        var azureApiKey: String = "",
        var azureRegion: String = "eastus",
        var googleApiKey: String = "",
        var deepseekApiKey: String = "",
        var qwenApiKey: String = "",
        var geminiApiKey: String = "",
        var ttsSpeed: Float = 1.0f,
        var ttsVolume: Float = 1.0f,
        
        // AI 配置
        var aiProvider: String = AIProvider.OPENAI.name,
        var aiOpenaiApiKey: String = "",
        var aiOpenaiModel: String = "gpt-3.5-turbo",
        var aiAzureOpenaiApiKey: String = "",
        var aiAzureOpenaiEndpoint: String = "",
        var aiClaudeApiKey: String = "",
        var aiGeminiApiKey: String = "",
        var aiDeepseekApiKey: String = "",
        var aiDeepseekModel: String = "deepseek-chat",
        var aiQwenApiKey: String = "",
        var aiQwenModel: String = "qwen-turbo",
        var aiTemperature: Float = 0.3f,
        var aiMaxTokens: Int = 2000
    )
    
    private var myState = State()
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }
    
    fun getTTSConfig(): TTSConfig {
        return TTSConfig(
            provider = TTSProvider.valueOf(myState.ttsProvider),
            openaiApiKey = myState.openaiApiKey,
            openaiVoice = OpenAIVoice.valueOf(myState.openaiVoice),
            azureApiKey = myState.azureApiKey,
            azureRegion = myState.azureRegion,
            googleApiKey = myState.googleApiKey,
            deepseekApiKey = myState.deepseekApiKey,
            qwenApiKey = myState.qwenApiKey,
            geminiApiKey = myState.geminiApiKey,
            speed = myState.ttsSpeed,
            volume = myState.ttsVolume
        )
    }
    
    fun updateTTSConfig(config: TTSConfig) {
        myState.ttsProvider = config.provider.name
        myState.openaiApiKey = config.openaiApiKey
        myState.openaiVoice = config.openaiVoice.name
        myState.azureApiKey = config.azureApiKey
        myState.azureRegion = config.azureRegion
        myState.googleApiKey = config.googleApiKey
        myState.deepseekApiKey = config.deepseekApiKey
        myState.qwenApiKey = config.qwenApiKey
        myState.geminiApiKey = config.geminiApiKey
        myState.ttsSpeed = config.speed
        myState.ttsVolume = config.volume
    }
    
    fun getAIConfig(): AIConfig {
        return AIConfig(
            provider = AIProvider.valueOf(myState.aiProvider),
            openaiApiKey = myState.aiOpenaiApiKey,
            openaiModel = myState.aiOpenaiModel,
            azureOpenaiApiKey = myState.aiAzureOpenaiApiKey,
            azureOpenaiEndpoint = myState.aiAzureOpenaiEndpoint,
            claudeApiKey = myState.aiClaudeApiKey,
            geminiApiKey = myState.aiGeminiApiKey,
            deepseekApiKey = myState.aiDeepseekApiKey,
            deepseekModel = myState.aiDeepseekModel,
            qwenApiKey = myState.aiQwenApiKey,
            qwenModel = myState.aiQwenModel,
            temperature = myState.aiTemperature,
            maxTokens = myState.aiMaxTokens
        )
    }
    
    fun updateAIConfig(config: AIConfig) {
        myState.aiProvider = config.provider.name
        myState.aiOpenaiApiKey = config.openaiApiKey
        myState.aiOpenaiModel = config.openaiModel
        myState.aiAzureOpenaiApiKey = config.azureOpenaiApiKey
        myState.aiAzureOpenaiEndpoint = config.azureOpenaiEndpoint
        myState.aiClaudeApiKey = config.claudeApiKey
        myState.aiGeminiApiKey = config.geminiApiKey
        myState.aiDeepseekApiKey = config.deepseekApiKey
        myState.aiDeepseekModel = config.deepseekModel
        myState.aiQwenApiKey = config.qwenApiKey
        myState.aiQwenModel = config.qwenModel
        myState.aiTemperature = config.temperature
        myState.aiMaxTokens = config.maxTokens
    }
    
    companion object {
        fun getInstance(): CodeAssistantSettings {
            return ApplicationManager.getApplication().getService(CodeAssistantSettings::class.java)
        }
    }
}
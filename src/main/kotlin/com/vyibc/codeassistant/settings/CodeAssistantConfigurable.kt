package com.vyibc.codeassistant.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import com.vyibc.codeassistant.config.*
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.*

class CodeAssistantConfigurable : Configurable {
    
    private var settingsPanel: JPanel? = null
    
    // TTS配置组件
    private lateinit var providerComboBox: ComboBox<TTSProvider>
    private lateinit var openaiApiKeyField: JBTextField
    private lateinit var openaiVoiceComboBox: ComboBox<OpenAIVoice>
    private lateinit var azureApiKeyField: JBTextField
    private lateinit var azureRegionField: JBTextField
    private lateinit var googleApiKeyField: JBTextField
    private lateinit var speedSlider: JSlider
    private lateinit var volumeSlider: JSlider
    
    // AI配置组件
    private lateinit var aiProviderComboBox: ComboBox<AIProvider>
    private lateinit var aiOpenaiApiKeyField: JBTextField
    private lateinit var aiOpenaiModelField: JBTextField
    private lateinit var aiAzureApiKeyField: JBTextField
    private lateinit var aiAzureEndpointField: JBTextField
    private lateinit var aiClaudeApiKeyField: JBTextField
    private lateinit var aiGeminiApiKeyField: JBTextField
    private lateinit var aiTemperatureSlider: JSlider
    private lateinit var aiMaxTokensField: JBTextField
    
    private lateinit var openaiPanel: JPanel
    private lateinit var azurePanel: JPanel
    private lateinit var googlePanel: JPanel
    
    private lateinit var aiOpenaiPanel: JPanel
    private lateinit var aiAzurePanel: JPanel
    private lateinit var aiClaudePanel: JPanel
    private lateinit var aiGeminiPanel: JPanel
    
    override fun getDisplayName(): String = "Code Assistant"
    
    override fun createComponent(): JComponent {
        // 创建TTS组件
        createTTSComponents()
        
        // 创建AI组件
        createAIComponents()
        
        // 创建服务配置面板
        createServicePanels()
        
        // 主面板
        val mainPanel = JTabbedPane().apply {
            addTab("语音合成 (TTS)", createTTSMainPanel())
            addTab("代码翻译 (AI)", createAIMainPanel())
        }
        
        settingsPanel = JPanel(BorderLayout()).apply {
            add(mainPanel, BorderLayout.CENTER)
        }
        
        return settingsPanel!!
    }
    
    private fun createTTSComponents() {
        providerComboBox = ComboBox(TTSProvider.values())
        openaiApiKeyField = JBTextField()
        openaiVoiceComboBox = ComboBox(OpenAIVoice.values())
        azureApiKeyField = JBTextField()
        azureRegionField = JBTextField()
        googleApiKeyField = JBTextField()
        
        speedSlider = JSlider(50, 200, 100).apply {
            majorTickSpacing = 50
            minorTickSpacing = 25
            paintTicks = true
            paintLabels = true
        }
        
        volumeSlider = JSlider(0, 100, 100).apply {
            majorTickSpacing = 25
            minorTickSpacing = 5
            paintTicks = true
            paintLabels = true
        }
    }
    
    private fun createAIComponents() {
        aiProviderComboBox = ComboBox(AIProvider.values())
        aiOpenaiApiKeyField = JBTextField()
        aiOpenaiModelField = JBTextField()
        aiAzureApiKeyField = JBTextField()
        aiAzureEndpointField = JBTextField()
        aiClaudeApiKeyField = JBTextField()
        aiGeminiApiKeyField = JBTextField()
        
        aiTemperatureSlider = JSlider(0, 100, 30).apply {
            majorTickSpacing = 25
            minorTickSpacing = 5
            paintTicks = true
            paintLabels = true
        }
        
        aiMaxTokensField = JBTextField()
    }
    
    private fun createTTSMainPanel(): JComponent {
        val mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("语音服务提供商:", providerComboBox)
            .addComponentFillVertically(createTTSServicePanel(), 0)
            .addSeparator()
            .addLabeledComponent("语速 (50%-200%):", speedSlider)
            .addLabeledComponent("音量 (0%-100%):", volumeSlider)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            
        return mainPanel
    }
    
    private fun createAIMainPanel(): JComponent {
        val mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("AI服务提供商:", aiProviderComboBox)
            .addComponentFillVertically(createAIServicePanel(), 0)
            .addSeparator()
            .addLabeledComponent("温度 (0-1.0):", aiTemperatureSlider)
            .addLabeledComponent("最大Token数:", aiMaxTokensField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            
        return mainPanel
    }
    
    private fun createServicePanels() {
        // TTS服务配置面板
        openaiPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("API Key:", openaiApiKeyField)
            .addLabeledComponent("语音类型:", openaiVoiceComboBox)
            .addComponent(JBLabel("<html><small>获取API Key: <a href='https://platform.openai.com/api-keys'>https://platform.openai.com/api-keys</a></small></html>"))
            .panel
            
        azurePanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("API Key:", azureApiKeyField)
            .addLabeledComponent("区域:", azureRegionField)
            .addComponent(JBLabel("<html><small>获取API Key: <a href='https://portal.azure.com'>Azure Portal</a></small></html>"))
            .panel
            
        googlePanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("API Key:", googleApiKeyField)
            .addComponent(JBLabel("<html><small>获取API Key: <a href='https://console.cloud.google.com'>Google Cloud Console</a></small></html>"))
            .panel
            
        // AI服务配置面板
        aiOpenaiPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("API Key:", aiOpenaiApiKeyField)
            .addLabeledComponent("模型:", aiOpenaiModelField)
            .addComponent(JBLabel("<html><small>支持模型: gpt-3.5-turbo, gpt-4, gpt-4-turbo等</small></html>"))
            .panel
            
        aiAzurePanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("API Key:", aiAzureApiKeyField)
            .addLabeledComponent("端点:", aiAzureEndpointField)
            .addComponent(JBLabel("<html><small>Azure OpenAI服务端点</small></html>"))
            .panel
            
        aiClaudePanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("API Key:", aiClaudeApiKeyField)
            .addComponent(JBLabel("<html><small>Anthropic Claude API Key (暂未实现)</small></html>"))
            .panel
            
        aiGeminiPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("API Key:", aiGeminiApiKeyField)
            .addComponent(JBLabel("<html><small>Google Gemini API Key (暂未实现)</small></html>"))
            .panel
    }
    
    private fun createTTSServicePanel(): JComponent {
        val tabbedPane = JTabbedPane()
        tabbedPane.addTab("OpenAI", openaiPanel)
        tabbedPane.addTab("Azure", azurePanel)  
        tabbedPane.addTab("Google", googlePanel)
        tabbedPane.addTab("系统TTS", JBLabel("使用本地系统语音合成，无需配置"))
        return tabbedPane
    }
    
    private fun createAIServicePanel(): JComponent {
        val tabbedPane = JTabbedPane()
        tabbedPane.addTab("OpenAI", aiOpenaiPanel)
        tabbedPane.addTab("Azure OpenAI", aiAzurePanel)
        tabbedPane.addTab("Claude", aiClaudePanel)
        tabbedPane.addTab("Gemini", aiGeminiPanel)
        return tabbedPane
    }
    
    override fun isModified(): Boolean {
        val settings = CodeAssistantSettings.getInstance()
        val currentTTSConfig = settings.getTTSConfig()
        val currentAIConfig = settings.getAIConfig()
        
        return providerComboBox.selectedItem != currentTTSConfig.provider ||
               openaiApiKeyField.text != currentTTSConfig.openaiApiKey ||
               openaiVoiceComboBox.selectedItem != currentTTSConfig.openaiVoice ||
               azureApiKeyField.text != currentTTSConfig.azureApiKey ||
               azureRegionField.text != currentTTSConfig.azureRegion ||
               googleApiKeyField.text != currentTTSConfig.googleApiKey ||
               speedSlider.value != (currentTTSConfig.speed * 100).toInt() ||
               volumeSlider.value != (currentTTSConfig.volume * 100).toInt() ||
               aiProviderComboBox.selectedItem != currentAIConfig.provider ||
               aiOpenaiApiKeyField.text != currentAIConfig.openaiApiKey ||
               aiOpenaiModelField.text != currentAIConfig.openaiModel ||
               aiAzureApiKeyField.text != currentAIConfig.azureOpenaiApiKey ||
               aiAzureEndpointField.text != currentAIConfig.azureOpenaiEndpoint ||
               aiClaudeApiKeyField.text != currentAIConfig.claudeApiKey ||
               aiGeminiApiKeyField.text != currentAIConfig.geminiApiKey ||
               aiTemperatureSlider.value != (currentAIConfig.temperature * 100).toInt() ||
               aiMaxTokensField.text != currentAIConfig.maxTokens.toString()
    }
    
    override fun apply() {
        val settings = CodeAssistantSettings.getInstance()
        
        // 保存TTS配置
        val ttsConfig = settings.getTTSConfig()
        ttsConfig.provider = providerComboBox.selectedItem as TTSProvider
        ttsConfig.openaiApiKey = openaiApiKeyField.text
        ttsConfig.openaiVoice = openaiVoiceComboBox.selectedItem as OpenAIVoice
        ttsConfig.azureApiKey = azureApiKeyField.text
        ttsConfig.azureRegion = azureRegionField.text
        ttsConfig.googleApiKey = googleApiKeyField.text
        ttsConfig.speed = speedSlider.value / 100.0f
        ttsConfig.volume = volumeSlider.value / 100.0f
        settings.updateTTSConfig(ttsConfig)
        
        // 保存AI配置
        val aiConfig = settings.getAIConfig()
        aiConfig.provider = aiProviderComboBox.selectedItem as AIProvider
        aiConfig.openaiApiKey = aiOpenaiApiKeyField.text
        aiConfig.openaiModel = aiOpenaiModelField.text.takeIf { it.isNotBlank() } ?: "gpt-3.5-turbo"
        aiConfig.azureOpenaiApiKey = aiAzureApiKeyField.text
        aiConfig.azureOpenaiEndpoint = aiAzureEndpointField.text
        aiConfig.claudeApiKey = aiClaudeApiKeyField.text
        aiConfig.geminiApiKey = aiGeminiApiKeyField.text
        aiConfig.temperature = aiTemperatureSlider.value / 100.0f
        aiConfig.maxTokens = aiMaxTokensField.text.toIntOrNull() ?: 2000
        settings.updateAIConfig(aiConfig)
    }
    
    override fun reset() {
        val settings = CodeAssistantSettings.getInstance()
        val ttsConfig = settings.getTTSConfig()
        val aiConfig = settings.getAIConfig()
        
        // 重置TTS配置
        providerComboBox.selectedItem = ttsConfig.provider
        openaiApiKeyField.text = ttsConfig.openaiApiKey
        openaiVoiceComboBox.selectedItem = ttsConfig.openaiVoice
        azureApiKeyField.text = ttsConfig.azureApiKey
        azureRegionField.text = ttsConfig.azureRegion
        googleApiKeyField.text = ttsConfig.googleApiKey
        speedSlider.value = (ttsConfig.speed * 100).toInt()
        volumeSlider.value = (ttsConfig.volume * 100).toInt()
        
        // 重置AI配置
        aiProviderComboBox.selectedItem = aiConfig.provider
        aiOpenaiApiKeyField.text = aiConfig.openaiApiKey
        aiOpenaiModelField.text = aiConfig.openaiModel
        aiAzureApiKeyField.text = aiConfig.azureOpenaiApiKey
        aiAzureEndpointField.text = aiConfig.azureOpenaiEndpoint
        aiClaudeApiKeyField.text = aiConfig.claudeApiKey
        aiGeminiApiKeyField.text = aiConfig.geminiApiKey
        aiTemperatureSlider.value = (aiConfig.temperature * 100).toInt()
        aiMaxTokensField.text = aiConfig.maxTokens.toString()
    }
}
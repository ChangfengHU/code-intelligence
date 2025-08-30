package com.vyibc.codeassistant.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.vyibc.codeassistant.chat.settings.CodeChatSettings
import com.vyibc.codeassistant.config.*
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ItemEvent
import javax.swing.*

class CodeAssistantConfigurable : Configurable {
    
    private var mainPanel: JPanel? = null
    private lateinit var tabbedPane: JTabbedPane
    
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
    
    // 代码对话配置组件
    private lateinit var systemPromptArea: JBTextArea
    private lateinit var maxTokensField: JBTextField
    private lateinit var temperatureField: JBTextField
    private lateinit var maxSessionsField: JBTextField
    private lateinit var maxMessagesField: JBTextField
    private lateinit var autoSaveField: JBTextField
    private lateinit var showTimestampCheck: JBCheckBox
    private lateinit var enableHighlightCheck: JBCheckBox
    private lateinit var dialogWidthField: JBTextField
    private lateinit var dialogHeightField: JBTextField
    private lateinit var showHistoryCheck: JBCheckBox
    private lateinit var showAIInteractionCheck: JBCheckBox
    private lateinit var enableDebugCheck: JBCheckBox
    
    private lateinit var openaiPanel: JPanel
    private lateinit var azurePanel: JPanel
    private lateinit var googlePanel: JPanel
    
    private lateinit var aiOpenaiPanel: JPanel
    private lateinit var aiAzurePanel: JPanel
    private lateinit var aiClaudePanel: JPanel
    private lateinit var aiGeminiPanel: JPanel
    
    override fun getDisplayName(): String = "Code Assistant"
    
    override fun createComponent(): JComponent {
        if (mainPanel == null) {
            mainPanel = JPanel(BorderLayout())
            
            tabbedPane = JTabbedPane()
            
            // 创建各个Tab
            val ttsPanel = createTTSPanel()
            val aiPanel = createAIPanel()
            val chatPanel = createChatPanel()
            
            tabbedPane.addTab("语音合成 (TTS)", ttsPanel)
            tabbedPane.addTab("代码翻译 (AI)", aiPanel)
            tabbedPane.addTab("代码对话", chatPanel)
            
            mainPanel!!.add(tabbedPane, BorderLayout.CENTER)
        }
        
        return mainPanel!!
    }
    
    private fun createTTSPanel(): JPanel {
        // 创建TTS组件
        createTTSComponents()
        
        val panel = JPanel(BorderLayout())
        
        val formBuilder = FormBuilder.createFormBuilder()
            .addLabeledComponent("语音服务提供商:", providerComboBox)
            .addComponent(openaiPanel)
            .addComponent(azurePanel)
            .addComponent(googlePanel)
            .addLabeledComponent("语速 (50%-200%):", speedSlider)
            .addLabeledComponent("音量 (0%-100%):", volumeSlider)
        
        panel.add(formBuilder.panel, BorderLayout.NORTH)
        
        return panel
    }
    
    private fun createAIPanel(): JPanel {
        // 创建AI组件
        createAIComponents()
        
        val panel = JPanel(BorderLayout())
        
        val formBuilder = FormBuilder.createFormBuilder()
            .addLabeledComponent("AI服务提供商:", aiProviderComboBox)
            .addComponent(aiOpenaiPanel)
            .addComponent(aiAzurePanel)
            .addComponent(aiClaudePanel)
            .addComponent(aiGeminiPanel)
            .addLabeledComponent("Temperature (0-2):", aiTemperatureSlider)
            .addLabeledComponent("Max Tokens:", aiMaxTokensField)
        
        panel.add(formBuilder.panel, BorderLayout.NORTH)
        
        return panel
    }
    
    private fun createChatPanel(): JPanel {
        val mainPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        
        // 设置默认约束
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = JBUI.insets(5)
        gbc.weightx = 0.0
        
        var row = 0
        
        // AI配置部分
        addSectionTitle(mainPanel, gbc, row++, "AI 配置")
        
        addLabelAndTextArea(mainPanel, gbc, row, "系统提示词:", 
            JBTextArea().also { systemPromptArea = it }.apply {
                rows = 8
                lineWrap = true
                wrapStyleWord = true
            })
        row += 2
        
        addLabelAndField(mainPanel, gbc, row++, "最大Token数:", 
            JBTextField().also { maxTokensField = it })
        
        addLabelAndField(mainPanel, gbc, row++, "Temperature (0.0-2.0):", 
            JBTextField().also { temperatureField = it })
        
        // 会话管理部分
        addSectionTitle(mainPanel, gbc, row++, "会话管理")
        
        addLabelAndField(mainPanel, gbc, row++, "最大会话数:", 
            JBTextField().also { maxSessionsField = it })
        
        addLabelAndField(mainPanel, gbc, row++, "每个会话最大消息数:", 
            JBTextField().also { maxMessagesField = it })
        
        addLabelAndField(mainPanel, gbc, row++, "自动保存间隔(秒):", 
            JBTextField().also { autoSaveField = it })
        
        // UI配置部分
        addSectionTitle(mainPanel, gbc, row++, "界面配置")
        
        addCheckBox(mainPanel, gbc, row++, "显示时间戳", 
            JBCheckBox().also { showTimestampCheck = it })
        
        addCheckBox(mainPanel, gbc, row++, "启用代码高亮", 
            JBCheckBox().also { enableHighlightCheck = it })
        
        addLabelAndField(mainPanel, gbc, row++, "对话框宽度:", 
            JBTextField().also { dialogWidthField = it })
        
        addLabelAndField(mainPanel, gbc, row++, "对话框高度:", 
            JBTextField().also { dialogHeightField = it })
        
        addCheckBox(mainPanel, gbc, row++, "启动时显示历史记录", 
            JBCheckBox().also { showHistoryCheck = it })
        
        // 调试配置部分
        addSectionTitle(mainPanel, gbc, row++, "调试配置")
        
        addCheckBox(mainPanel, gbc, row++, "显示AI交互详情", 
            JBCheckBox().also { showAIInteractionCheck = it })
        
        addCheckBox(mainPanel, gbc, row++, "启用调试模式", 
            JBCheckBox().also { enableDebugCheck = it })
        
        // 重置按钮
        gbc.gridy = row
        gbc.gridx = 0
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.CENTER
        val resetButton = JButton("恢复默认设置")
        resetButton.addActionListener { resetChatDefaults() }
        mainPanel.add(resetButton, gbc)
        
        val scrollPane = JScrollPane(mainPanel)
        scrollPane.border = null
        
        val wrapperPanel = JPanel(BorderLayout())
        wrapperPanel.add(scrollPane, BorderLayout.CENTER)
        return wrapperPanel
    }
    
    // 以下方法和原来的CodeAssistantConfigurable相同
    private fun createTTSComponents() {
        // TTS组件创建逻辑
        providerComboBox = ComboBox(TTSProvider.values())
        openaiApiKeyField = JBTextField()
        openaiVoiceComboBox = ComboBox(OpenAIVoice.values())
        azureApiKeyField = JBTextField()
        azureRegionField = JBTextField()
        googleApiKeyField = JBTextField()
        speedSlider = JSlider(50, 200, 100)
        volumeSlider = JSlider(0, 100, 80)
        
        // 创建面板
        openaiPanel = createOpenAIPanel()
        azurePanel = createAzurePanel()
        googlePanel = createGooglePanel()
        
        // 设置监听器
        providerComboBox.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                updatePanelVisibility()
            }
        }
    }
    
    private fun createAIComponents() {
        // AI组件创建逻辑
        aiProviderComboBox = ComboBox(AIProvider.values())
        aiOpenaiApiKeyField = JBTextField()
        aiOpenaiModelField = JBTextField()
        aiAzureApiKeyField = JBTextField()
        aiAzureEndpointField = JBTextField()
        aiClaudeApiKeyField = JBTextField()
        aiGeminiApiKeyField = JBTextField()
        aiTemperatureSlider = JSlider(0, 200, 70)
        aiMaxTokensField = JBTextField()
        
        // 创建面板
        aiOpenaiPanel = createAIOpenAIPanel()
        aiAzurePanel = createAIAzurePanel()
        aiClaudePanel = createAIClaudePanel()
        aiGeminiPanel = createAIGeminiPanel()
        
        // 设置监听器
        aiProviderComboBox.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                updateAIPanelVisibility()
            }
        }
    }
    
    private fun createOpenAIPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        val formBuilder = FormBuilder.createFormBuilder()
            .addLabeledComponent("API Key:", openaiApiKeyField)
            .addLabeledComponent("语音类型:", openaiVoiceComboBox)
        panel.add(formBuilder.panel)
        return panel
    }
    
    private fun createAzurePanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        val formBuilder = FormBuilder.createFormBuilder()
            .addLabeledComponent("API Key:", azureApiKeyField)
            .addLabeledComponent("Region:", azureRegionField)
        panel.add(formBuilder.panel)
        return panel
    }
    
    private fun createGooglePanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        val formBuilder = FormBuilder.createFormBuilder()
            .addLabeledComponent("API Key:", googleApiKeyField)
        panel.add(formBuilder.panel)
        return panel
    }
    
    private fun createAIOpenAIPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        val formBuilder = FormBuilder.createFormBuilder()
            .addLabeledComponent("API Key:", aiOpenaiApiKeyField)
            .addLabeledComponent("Model:", aiOpenaiModelField)
        panel.add(formBuilder.panel)
        return panel
    }
    
    private fun createAIAzurePanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        val formBuilder = FormBuilder.createFormBuilder()
            .addLabeledComponent("API Key:", aiAzureApiKeyField)
            .addLabeledComponent("Endpoint:", aiAzureEndpointField)
        panel.add(formBuilder.panel)
        return panel
    }
    
    private fun createAIClaudePanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        val formBuilder = FormBuilder.createFormBuilder()
            .addLabeledComponent("API Key:", aiClaudeApiKeyField)
        panel.add(formBuilder.panel)
        return panel
    }
    
    private fun createAIGeminiPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        val formBuilder = FormBuilder.createFormBuilder()
            .addLabeledComponent("API Key:", aiGeminiApiKeyField)
        panel.add(formBuilder.panel)
        return panel
    }
    
    private fun updatePanelVisibility() {
        val selected = providerComboBox.selectedItem as TTSProvider
        openaiPanel.isVisible = selected == TTSProvider.OPENAI
        azurePanel.isVisible = selected == TTSProvider.AZURE
        googlePanel.isVisible = selected == TTSProvider.GOOGLE
    }
    
    private fun updateAIPanelVisibility() {
        val selected = aiProviderComboBox.selectedItem as AIProvider
        aiOpenaiPanel.isVisible = selected == AIProvider.OPENAI
        aiAzurePanel.isVisible = selected == AIProvider.AZURE_OPENAI
        aiClaudePanel.isVisible = selected == AIProvider.CLAUDE
        aiGeminiPanel.isVisible = selected == AIProvider.GEMINI
    }
    
    // 代码对话配置相关方法
    private fun addSectionTitle(panel: JPanel, gbc: GridBagConstraints, row: Int, title: String) {
        gbc.gridy = row
        gbc.gridx = 0
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        
        val label = JBLabel("<html><b>$title</b></html>")
        label.border = JBUI.Borders.empty(10, 0, 5, 0)
        panel.add(label, gbc)
    }
    
    private fun addLabelAndField(panel: JPanel, gbc: GridBagConstraints, row: Int, 
                                labelText: String, field: JTextField) {
        gbc.gridy = row
        gbc.gridwidth = 1
        
        gbc.gridx = 0
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        panel.add(JBLabel(labelText), gbc)
        
        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(field, gbc)
    }
    
    private fun addLabelAndTextArea(panel: JPanel, gbc: GridBagConstraints, row: Int, 
                                   labelText: String, textArea: JBTextArea) {
        gbc.gridy = row
        gbc.gridx = 0
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(JBLabel(labelText), gbc)
        
        gbc.gridy = row + 1
        gbc.weighty = 0.3
        gbc.fill = GridBagConstraints.BOTH
        val scrollPane = JScrollPane(textArea)
        scrollPane.preferredSize = java.awt.Dimension(-1, 150)
        panel.add(scrollPane, gbc)
        gbc.weighty = 0.0
    }
    
    private fun addCheckBox(panel: JPanel, gbc: GridBagConstraints, row: Int, 
                           text: String, checkBox: JBCheckBox) {
        gbc.gridy = row
        gbc.gridx = 0
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.WEST
        
        checkBox.text = text
        panel.add(checkBox, gbc)
    }
    
    override fun isModified(): Boolean {
        val settings = CodeAssistantSettings.getInstance()
        val chatSettings = CodeChatSettings.getInstance()
        
        return isTTSModified(settings) || isAIModified(settings) || isChatModified(chatSettings)
    }
    
    private fun isTTSModified(settings: CodeAssistantSettings): Boolean {
        val state = settings.state
        return providerComboBox.selectedItem != TTSProvider.valueOf(state.ttsProvider) ||
               openaiApiKeyField.text != state.openaiApiKey ||
               openaiVoiceComboBox.selectedItem != OpenAIVoice.valueOf(state.openaiVoice) ||
               azureApiKeyField.text != state.azureApiKey ||
               azureRegionField.text != state.azureRegion ||
               googleApiKeyField.text != state.googleApiKey ||
               speedSlider.value != (state.ttsSpeed * 100).toInt() ||
               volumeSlider.value != (state.ttsVolume * 100).toInt()
    }
    
    private fun isAIModified(settings: CodeAssistantSettings): Boolean {
        val state = settings.state
        return aiProviderComboBox.selectedItem != AIProvider.valueOf(state.aiProvider) ||
               aiOpenaiApiKeyField.text != state.aiOpenaiApiKey ||
               aiOpenaiModelField.text != state.aiOpenaiModel ||
               aiAzureApiKeyField.text != state.aiAzureOpenaiApiKey ||
               aiAzureEndpointField.text != state.aiAzureOpenaiEndpoint ||
               aiClaudeApiKeyField.text != state.aiClaudeApiKey ||
               aiGeminiApiKeyField.text != state.aiGeminiApiKey ||
               aiTemperatureSlider.value != (state.aiTemperature * 100).toInt() ||
               aiMaxTokensField.text != state.aiMaxTokens.toString()
    }
    
    private fun isChatModified(chatSettings: CodeChatSettings): Boolean {
        val state = chatSettings.state
        return systemPromptArea.text != state.systemPrompt ||
               maxTokensField.text != state.maxTokens.toString() ||
               temperatureField.text != state.temperature.toString() ||
               maxSessionsField.text != state.maxSessions.toString() ||
               maxMessagesField.text != state.maxMessagesPerSession.toString() ||
               autoSaveField.text != state.autoSaveInterval.toString() ||
               showTimestampCheck.isSelected != state.showTimestamp ||
               enableHighlightCheck.isSelected != state.enableCodeHighlight ||
               dialogWidthField.text != state.dialogWidth.toString() ||
               dialogHeightField.text != state.dialogHeight.toString() ||
               showHistoryCheck.isSelected != state.showHistoryOnStart ||
               showAIInteractionCheck.isSelected != state.showAIInteraction ||
               enableDebugCheck.isSelected != state.enableDebugMode
    }
    
    override fun apply() {
        val settings = CodeAssistantSettings.getInstance()
        val chatSettings = CodeChatSettings.getInstance()
        
        applyTTSSettings(settings)
        applyAISettings(settings)
        applyChatSettings(chatSettings)
    }
    
    private fun applyTTSSettings(settings: CodeAssistantSettings) {
        val state = settings.state
        state.ttsProvider = (providerComboBox.selectedItem as TTSProvider).name
        state.openaiApiKey = openaiApiKeyField.text
        state.openaiVoice = (openaiVoiceComboBox.selectedItem as OpenAIVoice).name
        state.azureApiKey = azureApiKeyField.text
        state.azureRegion = azureRegionField.text
        state.googleApiKey = googleApiKeyField.text
        state.ttsSpeed = speedSlider.value / 100.0f
        state.ttsVolume = volumeSlider.value / 100.0f
    }
    
    private fun applyAISettings(settings: CodeAssistantSettings) {
        val state = settings.state
        state.aiProvider = (aiProviderComboBox.selectedItem as AIProvider).name
        state.aiOpenaiApiKey = aiOpenaiApiKeyField.text
        state.aiOpenaiModel = aiOpenaiModelField.text
        state.aiAzureOpenaiApiKey = aiAzureApiKeyField.text
        state.aiAzureOpenaiEndpoint = aiAzureEndpointField.text
        state.aiClaudeApiKey = aiClaudeApiKeyField.text
        state.aiGeminiApiKey = aiGeminiApiKeyField.text
        state.aiTemperature = aiTemperatureSlider.value / 100.0f
        state.aiMaxTokens = aiMaxTokensField.text.toIntOrNull() ?: state.aiMaxTokens
    }
    
    private fun applyChatSettings(chatSettings: CodeChatSettings) {
        val state = chatSettings.state
        try {
            state.systemPrompt = systemPromptArea.text
            state.maxTokens = maxTokensField.text.toIntOrNull() ?: state.maxTokens
            state.temperature = temperatureField.text.toDoubleOrNull() ?: state.temperature
            state.maxSessions = maxSessionsField.text.toIntOrNull() ?: state.maxSessions
            state.maxMessagesPerSession = maxMessagesField.text.toIntOrNull() ?: state.maxMessagesPerSession
            state.autoSaveInterval = autoSaveField.text.toIntOrNull() ?: state.autoSaveInterval
            state.showTimestamp = showTimestampCheck.isSelected
            state.enableCodeHighlight = enableHighlightCheck.isSelected
            state.dialogWidth = dialogWidthField.text.toIntOrNull() ?: state.dialogWidth
            state.dialogHeight = dialogHeightField.text.toIntOrNull() ?: state.dialogHeight
            state.showHistoryOnStart = showHistoryCheck.isSelected
            state.showAIInteraction = showAIInteractionCheck.isSelected
            state.enableDebugMode = enableDebugCheck.isSelected
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, "保存设置时出错: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }
    
    override fun reset() {
        val settings = CodeAssistantSettings.getInstance()
        val chatSettings = CodeChatSettings.getInstance()
        
        resetTTSSettings(settings)
        resetAISettings(settings)
        resetChatSettings(chatSettings)
        
        updatePanelVisibility()
        updateAIPanelVisibility()
    }
    
    private fun resetTTSSettings(settings: CodeAssistantSettings) {
        val state = settings.state
        providerComboBox.selectedItem = TTSProvider.valueOf(state.ttsProvider)
        openaiApiKeyField.text = state.openaiApiKey
        openaiVoiceComboBox.selectedItem = OpenAIVoice.valueOf(state.openaiVoice)
        azureApiKeyField.text = state.azureApiKey
        azureRegionField.text = state.azureRegion
        googleApiKeyField.text = state.googleApiKey
        speedSlider.value = (state.ttsSpeed * 100).toInt()
        volumeSlider.value = (state.ttsVolume * 100).toInt()
    }
    
    private fun resetAISettings(settings: CodeAssistantSettings) {
        val state = settings.state
        aiProviderComboBox.selectedItem = AIProvider.valueOf(state.aiProvider)
        aiOpenaiApiKeyField.text = state.aiOpenaiApiKey
        aiOpenaiModelField.text = state.aiOpenaiModel
        aiAzureApiKeyField.text = state.aiAzureOpenaiApiKey
        aiAzureEndpointField.text = state.aiAzureOpenaiEndpoint
        aiClaudeApiKeyField.text = state.aiClaudeApiKey
        aiGeminiApiKeyField.text = state.aiGeminiApiKey
        aiTemperatureSlider.value = (state.aiTemperature * 100).toInt()
        aiMaxTokensField.text = state.aiMaxTokens.toString()
    }
    
    private fun resetChatSettings(chatSettings: CodeChatSettings) {
        val state = chatSettings.state
        systemPromptArea.text = state.systemPrompt
        maxTokensField.text = state.maxTokens.toString()
        temperatureField.text = state.temperature.toString()
        maxSessionsField.text = state.maxSessions.toString()
        maxMessagesField.text = state.maxMessagesPerSession.toString()
        autoSaveField.text = state.autoSaveInterval.toString()
        showTimestampCheck.isSelected = state.showTimestamp
        enableHighlightCheck.isSelected = state.enableCodeHighlight
        dialogWidthField.text = state.dialogWidth.toString()
        dialogHeightField.text = state.dialogHeight.toString()
        showHistoryCheck.isSelected = state.showHistoryOnStart
        showAIInteractionCheck.isSelected = state.showAIInteraction
        enableDebugCheck.isSelected = state.enableDebugMode
    }
    
    private fun resetChatDefaults() {
        val defaultState = CodeChatSettings.State()
        systemPromptArea.text = defaultState.systemPrompt
        maxTokensField.text = defaultState.maxTokens.toString()
        temperatureField.text = defaultState.temperature.toString()
        maxSessionsField.text = defaultState.maxSessions.toString()
        maxMessagesField.text = defaultState.maxMessagesPerSession.toString()
        autoSaveField.text = defaultState.autoSaveInterval.toString()
        showTimestampCheck.isSelected = defaultState.showTimestamp
        enableHighlightCheck.isSelected = defaultState.enableCodeHighlight
        dialogWidthField.text = defaultState.dialogWidth.toString()
        dialogHeightField.text = defaultState.dialogHeight.toString()
        showHistoryCheck.isSelected = defaultState.showHistoryOnStart
        showAIInteractionCheck.isSelected = defaultState.showAIInteraction
        enableDebugCheck.isSelected = defaultState.enableDebugMode
    }
}
package com.vyibc.codeassistant.chat.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.FormBuilder
import com.vyibc.codeassistant.config.AIProvider
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

/**
 * 代码对话配置页面
 */
class CodeChatConfigurable : Configurable {
    
    private var panel: JPanel? = null
    private val settings = CodeChatSettings.getInstance()
    
    // UI组件
    private lateinit var systemPromptArea: JBTextArea
    private lateinit var maxTokensField: JBTextField
    private lateinit var temperatureField: JBTextField
    private lateinit var modelProviderCombo: JComboBox<AIProvider>
    private lateinit var modelOpenaiApiKeyField: JBTextField
    private lateinit var modelOpenaiModelField: JBTextField
    private lateinit var modelDeepseekApiKeyField: JBTextField
    private lateinit var modelDeepseekModelField: JBTextField
    private lateinit var modelGeminiApiKeyField: JBTextField
    private lateinit var modelGeminiModelField: JBTextField
    private lateinit var modelQwenApiKeyField: JBTextField
    private lateinit var modelQwenModelField: JBTextField
    private lateinit var modelCardPanel: JPanel
    private lateinit var modelCardLayout: CardLayout
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
    
    override fun getDisplayName(): String = "代码对话"
    
    override fun createComponent(): JComponent {
        if (panel == null) {
            panel = JPanel(BorderLayout())
            
            val mainPanel = JPanel(GridBagLayout())
            val gbc = GridBagConstraints()
            
            // 设置默认约束
            gbc.fill = GridBagConstraints.HORIZONTAL
            gbc.insets = JBUI.insets(5)
            gbc.weightx = 0.0
            
            var row = 0
            
            // AI配置部分
            addSectionTitle(mainPanel, gbc, row++, "AI 配置")
            
            addLabelAndTextArea(mainPanel, gbc, row++, "系统提示词:", 
                JBTextArea().also { systemPromptArea = it }.apply {
                    rows = 8
                    lineWrap = true
                    wrapStyleWord = true
                })
            
            addLabelAndField(mainPanel, gbc, row++, "最大Token数:", 
                JBTextField().also { maxTokensField = it })
            
            addLabelAndField(mainPanel, gbc, row++, "Temperature (0.0-2.0):", 
                JBTextField().also { temperatureField = it })

            addSectionTitle(mainPanel, gbc, row++, "模型配置")

            addLabelAndComponent(mainPanel, gbc, row, "模型提供商:",
                JComboBox(arrayOf(
                    AIProvider.OPENAI,
                    AIProvider.GEMINI,
                    AIProvider.DEEPSEEK,
                    AIProvider.QWEN
                )).also { modelProviderCombo = it })
            row++

            modelCardLayout = CardLayout()
            modelCardPanel = JPanel(modelCardLayout).apply {
                isOpaque = false
                border = JBUI.Borders.empty(0, 16, 16, 0)
                add(createModelOpenAIForm(), AIProvider.OPENAI.name)
                add(createModelGeminiForm(), AIProvider.GEMINI.name)
                add(createModelDeepSeekForm(), AIProvider.DEEPSEEK.name)
                add(createModelQwenForm(), AIProvider.QWEN.name)
            }

            gbc.gridy = row
            gbc.gridx = 0
            gbc.gridwidth = 2
            gbc.weightx = 1.0
            gbc.fill = GridBagConstraints.HORIZONTAL
            mainPanel.add(modelCardPanel, gbc)
            row++

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
            gbc.gridy = row++
            gbc.gridx = 0
            gbc.gridwidth = 2
            gbc.fill = GridBagConstraints.NONE
            gbc.anchor = GridBagConstraints.CENTER
            val resetButton = JButton("恢复默认设置")
            resetButton.addActionListener { resetToDefaults() }
            mainPanel.add(resetButton, gbc)

            val scrollPane = JScrollPane(mainPanel)
            scrollPane.border = null
            panel!!.add(scrollPane, BorderLayout.CENTER)

            modelProviderCombo.addActionListener { updateModelPanelVisibility() }
        }

        return panel!!
    }

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

    private fun addLabelAndComponent(panel: JPanel, gbc: GridBagConstraints, row: Int,
                                     labelText: String, component: JComponent) {
        gbc.gridy = row
        gbc.gridwidth = 1

        gbc.gridx = 0
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        panel.add(JBLabel(labelText), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(component, gbc)
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
        val state = settings.state
        return systemPromptArea.text != state.systemPrompt ||
                maxTokensField.text != state.maxTokens.toString() ||
                temperatureField.text != state.temperature.toString() ||
                (modelProviderCombo.selectedItem as AIProvider).name != state.modelProvider ||
                modelOpenaiApiKeyField.text != state.modelOpenaiApiKey ||
                modelOpenaiModelField.text != state.modelOpenaiModel ||
                modelDeepseekApiKeyField.text != state.modelDeepseekApiKey ||
                modelDeepseekModelField.text != state.modelDeepseekModel ||
                modelGeminiApiKeyField.text != state.modelGeminiApiKey ||
                modelGeminiModelField.text != state.modelGeminiModel ||
                modelQwenApiKeyField.text != state.modelQwenApiKey ||
                modelQwenModelField.text != state.modelQwenModel ||
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
        val state = settings.state
        try {
            state.systemPrompt = systemPromptArea.text
            state.maxTokens = maxTokensField.text.toIntOrNull() ?: state.maxTokens
            state.temperature = temperatureField.text.toDoubleOrNull() ?: state.temperature
            state.modelProvider = (modelProviderCombo.selectedItem as AIProvider).name
            state.modelOpenaiApiKey = modelOpenaiApiKeyField.text
            state.modelOpenaiModel = modelOpenaiModelField.text
            state.modelDeepseekApiKey = modelDeepseekApiKeyField.text
            state.modelDeepseekModel = modelDeepseekModelField.text
            state.modelGeminiApiKey = modelGeminiApiKeyField.text
            state.modelGeminiModel = modelGeminiModelField.text
            state.modelQwenApiKey = modelQwenApiKeyField.text
            state.modelQwenModel = modelQwenModelField.text
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
        val state = settings.state
        systemPromptArea.text = state.systemPrompt
        maxTokensField.text = state.maxTokens.toString()
        temperatureField.text = state.temperature.toString()
        modelProviderCombo.selectedItem = AIProvider.valueOf(state.modelProvider)
        modelOpenaiApiKeyField.text = state.modelOpenaiApiKey
        modelOpenaiModelField.text = state.modelOpenaiModel
        modelDeepseekApiKeyField.text = state.modelDeepseekApiKey
        modelDeepseekModelField.text = state.modelDeepseekModel
        modelGeminiApiKeyField.text = state.modelGeminiApiKey
        modelGeminiModelField.text = state.modelGeminiModel
        modelQwenApiKeyField.text = state.modelQwenApiKey
        modelQwenModelField.text = state.modelQwenModel
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

        updateModelPanelVisibility()
    }

    private fun resetToDefaults() {
        val defaultState = CodeChatSettings.State()
        systemPromptArea.text = defaultState.systemPrompt
        maxTokensField.text = defaultState.maxTokens.toString()
        temperatureField.text = defaultState.temperature.toString()
        modelProviderCombo.selectedItem = AIProvider.valueOf(defaultState.modelProvider)
        modelOpenaiApiKeyField.text = defaultState.modelOpenaiApiKey
        modelOpenaiModelField.text = defaultState.modelOpenaiModel
        modelDeepseekApiKeyField.text = defaultState.modelDeepseekApiKey
        modelDeepseekModelField.text = defaultState.modelDeepseekModel
        modelGeminiApiKeyField.text = defaultState.modelGeminiApiKey
        modelGeminiModelField.text = defaultState.modelGeminiModel
        modelQwenApiKeyField.text = defaultState.modelQwenApiKey
        modelQwenModelField.text = defaultState.modelQwenModel
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

        updateModelPanelVisibility()
    }

    private fun updateModelPanelVisibility() {
        if (!::modelCardLayout.isInitialized) return
        val provider = modelProviderCombo.selectedItem as? AIProvider ?: return
        modelCardLayout.show(modelCardPanel, provider.name)
    }

    private fun createModelOpenAIForm(): JPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent("API Key:", JBTextField().also { modelOpenaiApiKeyField = it })
        .addLabeledComponent("模型:", JBTextField().also { modelOpenaiModelField = it })
        .panel

    private fun createModelDeepSeekForm(): JPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent("API Key:", JBTextField().also { modelDeepseekApiKeyField = it })
        .addLabeledComponent("模型:", JBTextField().also { modelDeepseekModelField = it })
        .panel

    private fun createModelGeminiForm(): JPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent("API Key:", JBTextField().also { modelGeminiApiKeyField = it })
        .addLabeledComponent("模型:", JBTextField().also { modelGeminiModelField = it })
        .panel

    private fun createModelQwenForm(): JPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent("API Key:", JBTextField().also { modelQwenApiKeyField = it })
        .addLabeledComponent("模型:", JBTextField().also { modelQwenModelField = it })
        .panel
}

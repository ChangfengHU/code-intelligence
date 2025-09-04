package com.vyibc.codeassistant.chat.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.vyibc.codeassistant.chat.settings.CodeChatSettings
import com.vyibc.codeassistant.chat.model.ChatMessage
import com.vyibc.codeassistant.chat.model.CodeContext
import com.vyibc.codeassistant.chat.model.MessageType
import com.vyibc.codeassistant.settings.CodeAssistantSettings
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AI对话服务
 */
@Service
class AIConversationService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    
    private val config = CodeAssistantSettings.getInstance()
    private val chatSettings = CodeChatSettings.getInstance()
    private val baseUrl = "https://api.openai.com/v1/chat/completions"
    
    /**
     * 发送消息并获取回复
     */
    suspend fun sendMessage(
        message: String,
        codeContext: CodeContext,
        conversationHistory: List<ChatMessage>
    ): String {
        // 检查API Key配置
        val apiKey = config.state.aiOpenaiApiKey
        val model = config.state.aiOpenaiModel
        
        println("检查AI配置: API Key长度=${apiKey.length}, Model=$model") // 调试日志
        
        if (apiKey.isBlank()) {
            throw IOException("代码对话功能需要在 Tools -> Code Assistant -> 代码翻译(AI) 中配置 OpenAI API Key")
        }
        
        if (model.isBlank()) {
            throw IOException("请在 Tools -> Code Assistant -> 代码翻译(AI) 中配置 OpenAI Model")
        }
        
        println("开始调用AI服务...") // 调试日志
        
        try {
            val result = requestConversation(message, codeContext, conversationHistory)
            println("AI服务调用成功，响应长度: ${result.length}") // 调试日志
            return result
        } catch (e: Exception) {
            println("AI服务调用失败: ${e.message}") // 调试日志
            e.printStackTrace()
            throw IOException("AI对话失败: ${e.message}")
        }
    }
    
    /**
     * 执行AI对话请求
     */
    private fun requestConversation(
        message: String,
        codeContext: CodeContext,
        conversationHistory: List<ChatMessage>
    ): String {
        println("开始构建AI请求...") // 调试日志
        
        val messages = buildMessages(message, codeContext, conversationHistory)
        
        val json = JsonObject().apply {
            addProperty("model", config.state.aiOpenaiModel)
            add("messages", messages)
            addProperty("temperature", chatSettings.state.temperature)
            addProperty("max_tokens", chatSettings.state.maxTokens)
        }
        
        // 调试日志：格式化 JSON
        if (chatSettings.state.showAIInteraction || chatSettings.state.enableDebugMode) {
            try {
                val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
                println("请求JSON:\n" + gson.toJson(com.google.gson.JsonParser.parseString(json.toString())))
            } catch (e: Exception) {
                println("请求JSON构建(非格式化): ${json}")
            }
        }
        
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(baseUrl)
            .header("Authorization", "Bearer ${config.state.aiOpenaiApiKey}")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        println("发送HTTP请求到: $baseUrl") // 调试日志
        
        client.newCall(request).execute().use { response ->
            println("收到响应: ${response.code} ${response.message}") // 调试日志
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                println("错误响应: $errorBody") // 调试日志
                throw IOException("AI请求失败: ${response.code} ${response.message}\n错误详情: $errorBody")
            }
            
            val responseBody = response.body?.string() ?: throw IOException("响应内容为空")
            println("响应内容长度: ${responseBody.length}") // 调试日志
            
            // 调试：打印返回 JSON（截断避免刷屏）
            if (chatSettings.state.showAIInteraction || chatSettings.state.enableDebugMode) {
                try {
                    val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
                    val pretty = gson.toJson(com.google.gson.JsonParser.parseString(responseBody))
                    println("响应JSON(截断显示):\n" + pretty.take(4000))
                } catch (e: Exception) {
                    println("响应原文(截断):\n" + responseBody.take(4000))
                }
            }
            
            return parseResponse(responseBody)
        }
    }
    
    /**
     * 构建消息数组
     */
    // 构建对话消息，调试时记录入参是否包含历史消息
    private fun buildMessages(
        message: String,
        codeContext: CodeContext,
        conversationHistory: List<ChatMessage>
    ): JsonArray {
        val messages = JsonArray()
        
        // 系统提示词
        messages.add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", chatSettings.state.systemPrompt)
        })
        
        // 代码上下文信息
        if (codeContext.selectedCode.isNotEmpty()) {
            messages.add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", buildContextPrompt(codeContext))
            })
        }
        
        // 对话历史
        if (chatSettings.state.showAIInteraction || chatSettings.state.enableDebugMode) {
            println("历史消息条数: ${conversationHistory.size}")
        }
        conversationHistory.forEach { historyMessage ->
            when (historyMessage.type) {
                MessageType.USER, MessageType.CODE_ANALYSIS -> {
                    messages.add(JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", historyMessage.content)
                    })
                }
                MessageType.ASSISTANT -> {
                    messages.add(JsonObject().apply {
                        addProperty("role", "assistant")
                        addProperty("content", historyMessage.content)
                    })
                }
                MessageType.SYSTEM -> {
                    // 系统消息不加入对话历史
                }
            }
        }
        
        // 当前用户消息
        if (message.isNotEmpty()) {
            messages.add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", message)
            })
        }

        if (chatSettings.state.showAIInteraction || chatSettings.state.enableDebugMode) {
            val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
            println("最终入参messages:\n" + gson.toJson(messages))
        }
        
        return messages
    }
    
    /**
     * 构建代码上下文提示词
     */
    private fun buildContextPrompt(context: CodeContext): String {
        val builder = StringBuilder()
        
        builder.append("## 代码上下文信息\n\n")
        
        builder.append("### 当前类: ${context.className}\n")
        if (context.methodName != null) {
            builder.append("### 当前方法: ${context.methodName}\n")
        }
        
        builder.append("### 用户选中的代码:\n")
        builder.append("```${getLanguageFromClassName(context.className)}\n")
        builder.append(context.selectedCode)
        builder.append("\n```\n\n")
        
        if (context.imports.isNotEmpty()) {
            builder.append("### 相关导入:\n")
            context.imports.take(10).forEach { import ->
                builder.append("- $import\n")
            }
            builder.append("\n")
        }
        
        if (context.dependencies.isNotEmpty()) {
            builder.append("### 相关依赖类:\n")
            context.dependencies.forEach { dep ->
                builder.append("**${dep.className}**\n")
                builder.append("方法: ${dep.methods.joinToString(", ")}\n")
                builder.append("```\n${dep.code.take(200)}${if (dep.code.length > 200) "..." else ""}\n```\n\n")
            }
        }
        
        if (context.callChain.isNotEmpty()) {
            builder.append("### 调用链信息:\n")
            context.callChain.forEach { call ->
                builder.append("**${call.className}.${call.methodName}()**\n")
                builder.append("```\n${call.code.take(200)}${if (call.code.length > 200) "..." else ""}\n```\n\n")
            }
        }
        
        builder.append("### 完整类上下文:\n")
        builder.append("```${getLanguageFromClassName(context.className)}\n")
        // 限制类上下文大小避免token过多
        val classContext = context.classContext.take(2000)
        builder.append(classContext)
        if (context.classContext.length > 2000) {
            builder.append("\n... (类内容过长，已截断)")
        }
        builder.append("\n```\n")
        
        return builder.toString()
    }
    
    /**
     * 根据类名推断编程语言
     */
    private fun getLanguageFromClassName(className: String): String {
        return when {
            className.contains(".kt") -> "kotlin"
            className.contains(".java") -> "java"
            else -> "java"
        }
    }
    
    /**
     * 解析AI响应
     */
    private fun parseResponse(responseBody: String): String {
        val gson = com.google.gson.Gson()
        val response = gson.fromJson(responseBody, JsonObject::class.java)
        
        val choices = response.getAsJsonArray("choices")
        if (choices.size() == 0) {
            throw IOException("AI响应格式错误：没有选择项")
        }
        
        val choice = choices[0].asJsonObject
        val message = choice.getAsJsonObject("message")
        return message.get("content").asString
    }
    
    companion object {
        fun getInstance(): AIConversationService = service()
    }
}
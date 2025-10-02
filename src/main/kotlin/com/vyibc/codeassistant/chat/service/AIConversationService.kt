package com.vyibc.codeassistant.chat.service

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.vyibc.codeassistant.chat.model.ChatMessage
import com.vyibc.codeassistant.chat.model.CodeContext
import com.vyibc.codeassistant.chat.model.MessageType
import com.vyibc.codeassistant.chat.settings.CodeChatSettings
import com.vyibc.codeassistant.config.AIProvider
import com.vyibc.codeassistant.settings.CodeAssistantSettings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AI对话服务，支持多种模型提供商
 */
@Service
class AIConversationService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val settings = CodeAssistantSettings.getInstance()
    private val chatSettings = CodeChatSettings.getInstance()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun sendMessage(
        message: String,
        codeContext: CodeContext,
        conversationHistory: List<ChatMessage>
    ): String {
        val aiConfig = settings.getAIConfig()
        val payload = buildConversationPayload(message, codeContext, conversationHistory)
        val provider = aiConfig.provider

        try {
            return when (provider) {
                AIProvider.OPENAI -> requestOpenAI(aiConfig, payload)
                AIProvider.GEMINI -> requestGemini(aiConfig, payload)
                AIProvider.DEEPSEEK -> requestDeepSeek(aiConfig, payload)
                AIProvider.QWEN -> requestQwen(aiConfig, payload)
                AIProvider.AZURE_OPENAI -> throw IOException("Azure OpenAI 对话暂未实现，请选择其他提供商")
                AIProvider.CLAUDE -> throw IOException("Claude 对话暂未实现，请选择其他提供商")
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("AI对话失败: ${e.message}", e)
        }
    }

    private fun requestOpenAI(config: com.vyibc.codeassistant.config.AIConfig, payload: ConversationPayload): String {
        if (config.openaiApiKey.isBlank()) {
            throw IOException("请在设置中配置 OpenAI API Key")
        }
        if (config.openaiModel.isBlank()) {
            throw IOException("请在设置中配置 OpenAI 模型")
        }

        val messages = payload.toOpenAIStyleMessages()
        val json = JsonObject().apply {
            addProperty("model", config.openaiModel)
            add("messages", messages)
            addProperty("temperature", chatSettings.state.temperature)
            addProperty("max_tokens", chatSettings.state.maxTokens)
        }

        val request = Request.Builder()
            .url(OPENAI_URL)
            .header("Authorization", "Bearer ${config.openaiApiKey}")
            .header("Content-Type", JSON_MEDIA_TYPE_STRING)
            .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        logRequest(AIProvider.OPENAI, request, json)
        return executeRequest(request) { parseOpenAIStyleResponse(it) }
    }

    private fun requestDeepSeek(config: com.vyibc.codeassistant.config.AIConfig, payload: ConversationPayload): String {
        if (config.deepseekApiKey.isBlank()) {
            throw IOException("请在设置中配置 DeepSeek API Key")
        }
        if (config.deepseekModel.isBlank()) {
            throw IOException("请在设置中配置 DeepSeek 模型")
        }

        val messages = payload.toOpenAIStyleMessages()
        val json = JsonObject().apply {
            addProperty("model", config.deepseekModel)
            add("messages", messages)
            addProperty("temperature", chatSettings.state.temperature)
            addProperty("max_tokens", chatSettings.state.maxTokens)
        }

        val request = Request.Builder()
            .url(DEEPSEEK_URL)
            .header("Authorization", "Bearer ${config.deepseekApiKey}")
            .header("Content-Type", JSON_MEDIA_TYPE_STRING)
            .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        logRequest(AIProvider.DEEPSEEK, request, json)
        return executeRequest(request) { parseOpenAIStyleResponse(it) }
    }

    private fun requestGemini(config: com.vyibc.codeassistant.config.AIConfig, payload: ConversationPayload): String {
        if (config.geminiApiKey.isBlank()) {
            throw IOException("请在设置中配置 Gemini API Key")
        }
        if (config.geminiModel.isBlank()) {
            throw IOException("请在设置中配置 Gemini 模型")
        }

        val systemPrompt = payload.systemMessages.joinToString("\n\n").trim()
        val contents = JsonArray().apply {
            payload.chatMessages.forEach { message ->
                val role = when (message.role) {
                    ProviderRole.USER -> "user"
                    ProviderRole.ASSISTANT -> "model"
                    ProviderRole.SYSTEM -> "system"
                }
                add(JsonObject().apply {
                    addProperty("role", role)
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", message.content) })
                    })
                })
            }
        }

        val json = JsonObject().apply {
            if (systemPrompt.isNotEmpty()) {
                add("systemInstruction", JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", systemPrompt) })
                    })
                })
            }
            add("contents", contents)
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", chatSettings.state.temperature)
                addProperty("maxOutputTokens", chatSettings.state.maxTokens)
            })
        }

        val url = "${GEMINI_URL_PREFIX}${config.geminiModel}:generateContent?key=${config.geminiApiKey}"
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", JSON_MEDIA_TYPE_STRING)
            .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        logRequest(AIProvider.GEMINI, request, json)
        return executeRequest(request) { parseGeminiResponse(it) }
    }

    private fun requestQwen(config: com.vyibc.codeassistant.config.AIConfig, payload: ConversationPayload): String {
        if (config.qwenApiKey.isBlank()) {
            throw IOException("请在设置中配置 通义千问 API Key")
        }
        if (config.qwenModel.isBlank()) {
            throw IOException("请在设置中配置 通义千问 模型")
        }

        val messages = JsonArray().apply {
            payload.systemMessages.forEach { systemMessage ->
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", systemMessage)
                })
            }
            payload.chatMessages.forEach { message ->
                val role = when (message.role) {
                    ProviderRole.USER -> "user"
                    ProviderRole.ASSISTANT -> "assistant"
                    ProviderRole.SYSTEM -> "system"
                }
                add(JsonObject().apply {
                    addProperty("role", role)
                    addProperty("content", message.content)
                })
            }
        }

        val json = JsonObject().apply {
            addProperty("model", config.qwenModel)
            add("input", JsonObject().apply { add("messages", messages) })
            add("parameters", JsonObject().apply {
                addProperty("temperature", chatSettings.state.temperature)
                addProperty("max_tokens", chatSettings.state.maxTokens)
                addProperty("result_format", "text")
            })
        }

        val request = Request.Builder()
            .url(QWEN_URL)
            .header("Authorization", "Bearer ${config.qwenApiKey}")
            .header("Content-Type", JSON_MEDIA_TYPE_STRING)
            .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        logRequest(AIProvider.QWEN, request, json)
        return executeRequest(request) { parseQwenResponse(it) }
    }

    private fun buildConversationPayload(
        message: String,
        codeContext: CodeContext,
        conversationHistory: List<ChatMessage>
    ): ConversationPayload {
        val systemMessages = mutableListOf<String>()
        val chatMessages = mutableListOf<ProviderMessage>()

        val basePrompt = chatSettings.state.systemPrompt.trim()
        if (basePrompt.isNotEmpty()) {
            systemMessages += basePrompt
        }

        if (codeContext.selectedCode.isNotBlank()) {
            systemMessages += buildContextPrompt(codeContext)
        }

        conversationHistory.forEach { history ->
            if (history.content.isBlank()) return@forEach
            when (history.type) {
                MessageType.USER, MessageType.CODE_ANALYSIS -> chatMessages += ProviderMessage(ProviderRole.USER, history.content)
                MessageType.ASSISTANT -> chatMessages += ProviderMessage(ProviderRole.ASSISTANT, history.content)
                MessageType.SYSTEM -> systemMessages += history.content
            }
        }

        val trimmed = message.trim()
        if (trimmed.isNotEmpty()) {
            val last = chatMessages.lastOrNull()
            if (last?.role != ProviderRole.USER || last.content != trimmed) {
                chatMessages += ProviderMessage(ProviderRole.USER, trimmed)
            }
        }

        return ConversationPayload(systemMessages, chatMessages)
    }

    private fun ConversationPayload.toOpenAIStyleMessages(): JsonArray {
        val messages = JsonArray()
        systemMessages.forEach { systemMessage ->
            messages.add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", systemMessage)
            })
        }
        chatMessages.forEach { message ->
            val role = when (message.role) {
                ProviderRole.USER -> "user"
                ProviderRole.ASSISTANT -> "assistant"
                ProviderRole.SYSTEM -> "system"
            }
            messages.add(JsonObject().apply {
                addProperty("role", role)
                addProperty("content", message.content)
            })
        }
        return messages
    }

    private fun executeRequest(request: Request, parser: (String) -> String): String {
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val sanitizedUrl = request.url.newBuilder().query(null).build()
                throw IOException("AI请求失败: ${response.code} ${response.message}\nURL: $sanitizedUrl\n错误详情: ${responseBody.ifEmpty { "无" }}")
            }

            logResponse(request, responseBody)
            if (responseBody.isEmpty()) {
                throw IOException("响应内容为空")
            }
            return parser(responseBody)
        }
    }

    private fun parseOpenAIStyleResponse(responseBody: String): String {
        try {
            val json = JsonParser.parseString(responseBody).asJsonObject
            val choices = json.getAsJsonArray("choices") ?: throw IOException("AI响应格式错误：缺少choices")
            if (choices.size() == 0) {
                throw IOException("AI响应格式错误：choices为空")
            }
            val choice = choices[0].asJsonObject
            val message = choice.getAsJsonObject("message") ?: throw IOException("AI响应格式错误：缺少message")
            return message.get("content")?.asString?.trim().orEmpty()
        } catch (e: Exception) {
            throw if (e is IOException) e else IOException("解析AI响应失败: ${e.message}", e)
        }
    }

    private fun parseGeminiResponse(responseBody: String): String {
        try {
            val json = JsonParser.parseString(responseBody).asJsonObject
            val candidates = json.getAsJsonArray("candidates") ?: throw IOException("Gemini响应格式错误：缺少candidates")
            if (candidates.size() == 0) {
                throw IOException("Gemini响应为空")
            }
            val first = candidates[0].asJsonObject
            val content = first.getAsJsonObject("content") ?: throw IOException("Gemini响应格式错误：缺少content")
            val parts = content.getAsJsonArray("parts") ?: throw IOException("Gemini响应格式错误：缺少parts")
            val texts = parts.mapNotNull { part ->
                part.asJsonObject.get("text")?.asString
            }
            if (texts.isEmpty()) {
                throw IOException("Gemini响应内容为空")
            }
            return texts.joinToString("\n").trim()
        } catch (e: Exception) {
            throw if (e is IOException) e else IOException("解析Gemini响应失败: ${e.message}", e)
        }
    }

    private fun parseQwenResponse(responseBody: String): String {
        try {
            val json = JsonParser.parseString(responseBody).asJsonObject
            val output = json.getAsJsonObject("output") ?: throw IOException("通义千问响应格式错误：缺少output")
            if (output.has("text")) {
                val text = output.get("text").asString
                if (text.isNotBlank()) return text.trim()
            }
            if (output.has("choices")) {
                val choices = output.getAsJsonArray("choices")
                val firstChoice = choices.firstOrNull()?.asJsonObject
                val message = firstChoice?.getAsJsonObject("message")
                val content = message?.get("content")?.asString
                if (!content.isNullOrBlank()) return content.trim()
            }
            throw IOException("通义千问响应内容为空")
        } catch (e: Exception) {
            throw if (e is IOException) e else IOException("解析通义千问响应失败: ${e.message}", e)
        }
    }

    private fun buildContextPrompt(context: CodeContext): String {
        val builder = StringBuilder()
        builder.append("## 代码上下文信息\n\n")
        builder.append("### 当前类: ${context.className}\n")
        context.methodName?.let { builder.append("### 当前方法: $it\n") }
        builder.append("### 用户选中的代码:\n")
        builder.append("```${getLanguageFromClassName(context.className)}\n")
        builder.append(context.selectedCode)
        builder.append("\n```\n\n")

        if (context.imports.isNotEmpty()) {
            builder.append("### 相关导入:\n")
            context.imports.take(10).forEach { builder.append("- $it\n") }
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
        val classContext = context.classContext.take(2000)
        builder.append(classContext)
        if (context.classContext.length > 2000) {
            builder.append("\n... (类内容过长，已截断)")
        }
        builder.append("\n```\n")
        return builder.toString()
    }

    private fun getLanguageFromClassName(className: String): String {
        return when {
            className.contains(".kt") -> "kotlin"
            className.contains(".java") -> "java"
            className.contains(".py") -> "python"
            className.contains(".js") -> "javascript"
            className.contains(".ts") -> "typescript"
            className.contains(".go") -> "go"
            className.contains(".cpp") || className.contains(".hpp") || className.contains(".c") -> "cpp"
            else -> "java"
        }
    }

    private fun logRequest(provider: AIProvider, request: Request, body: JsonObject) {
        if (!shouldLog()) return
        val sanitizedUrl = request.url.newBuilder().query(null).build()
        println("[CodeChat][$provider] 请求 URL: $sanitizedUrl")
        logJson("请求体", body.toString())
    }

    private fun logResponse(request: Request, body: String) {
        if (!shouldLog()) return
        val sanitizedUrl = request.url.newBuilder().query(null).build()
        println("[CodeChat] 响应 URL: $sanitizedUrl")
        logJson("响应体", body)
    }

    private fun logJson(title: String, raw: String) {
        if (!shouldLog()) return
        try {
            val pretty = gson.toJson(JsonParser.parseString(raw))
            println("[CodeChat] $title:\n$pretty")
        } catch (_: Exception) {
            println("[CodeChat] $title: $raw")
        }
    }

    private fun shouldLog(): Boolean = chatSettings.state.showAIInteraction || chatSettings.state.enableDebugMode

    private data class ConversationPayload(
        val systemMessages: List<String>,
        val chatMessages: List<ProviderMessage>
    )

    private data class ProviderMessage(
        val role: ProviderRole,
        val content: String
    )

    private enum class ProviderRole {
        USER,
        ASSISTANT,
        SYSTEM
    }

    companion object {
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        private const val DEEPSEEK_URL = "https://api.deepseek.com/chat/completions"
        private const val GEMINI_URL_PREFIX = "https://generativelanguage.googleapis.com/v1beta/models/"
        private const val QWEN_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val JSON_MEDIA_TYPE_STRING = "application/json"

        fun getInstance(): AIConversationService = service()
    }
}

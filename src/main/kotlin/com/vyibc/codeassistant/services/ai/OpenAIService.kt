package com.vyibc.codeassistant.services.ai

import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.vyibc.codeassistant.config.AIConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAIService(private val config: AIConfig) : AIService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
        
    private val baseUrl = "https://api.openai.com/v1/chat/completions"
    
    private val systemPrompt = """
        角色：你是一名精通多种编程语言的专家级软件工程师，并且也是一位专业的双语（英文、中文）技术文档翻译者。
        
        任务：我将提供一段代码，你需要逐行检查并翻译其中的人类可读文本。
        
        翻译规则：
        1. 翻译对象：只翻译代码中的注释（以 # 开头的内容）、文档字符串（在三引号之间的内容）以及代码中作为参数或返回值的用户提示字符串（例如 raise Exception 的错误信息）。
        2. 禁止翻译：绝对不要翻译任何代码的语法结构，包括但不限于：变量名、函数名、类名、模块名、关键字（如 def, class, if 等）。
        3. 保持格式：完全保留原始代码的缩进、结构和所有代码部分。每一行的缩进必须与原始代码完全相同。
        4. 输出要求：直接输出包含翻译后文本的完整代码块，不要添加额外的解释或说明，也不要修改任何空白字符或缩进。
        5. 格式要求：输出的代码必须保持与输入完全相同的行首缩进和换行格式。
    """.trimIndent()
    
    override fun translateCode(code: String): String {
        if (config.openaiApiKey.isBlank()) {
            throw IOException("OpenAI API Key未配置")
        }
        
        try {
            return requestTranslation(code)
        } catch (e: Exception) {
            throw IOException("AI代码翻译失败: ${e.message}")
        }
    }
    
    private fun requestTranslation(code: String): String {
        val messages = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", systemPrompt)
            })
            add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", "这是你需要翻译的代码：\n\n```\n$code\n```")
            })
        }
        
        val json = JsonObject().apply {
            addProperty("model", config.openaiModel)
            add("messages", messages)
            addProperty("temperature", config.temperature)
            addProperty("max_tokens", config.maxTokens)
        }
        
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(baseUrl)
            .header("Authorization", "Bearer ${config.openaiApiKey}")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()
            
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "未知错误"
            throw IOException("OpenAI API请求失败 (${response.code}): $errorBody")
        }
        
        val responseBody = response.body?.string() ?: throw IOException("响应体为空")
        return parseOpenAIResponse(responseBody)
    }
    
    private fun parseOpenAIResponse(jsonResponse: String): String {
        try {
            val responseJson = JsonParser.parseString(jsonResponse).asJsonObject
            val choices = responseJson.getAsJsonArray("choices")
            
            if (choices.size() == 0) {
                throw IOException("OpenAI返回空响应")
            }
            
            val firstChoice = choices[0].asJsonObject
            val message = firstChoice.getAsJsonObject("message")
            val content = message.get("content").asString
            
            // 智能清理：移除代码块标记但保持内部格式
            return cleanCodeBlockResponse(content)
            
        } catch (e: Exception) {
            throw IOException("解析OpenAI响应失败: ${e.message}")
        }
    }
    
    /**
     * 智能清理代码块响应，保持内部格式和缩进
     */
    private fun cleanCodeBlockResponse(content: String): String {
        // 移除代码块标记
        var cleaned = content
            .replace(Regex("```\\w*\\n?"), "") // 移除开头的代码块标记
            .replace(Regex("\\n?```$"), "")   // 移除结尾的代码块标记
        
        // 按行分割
        val lines = cleaned.split("\n")
        
        // 移除开头的空行，但保持后续行的所有格式
        var startIndex = 0
        while (startIndex < lines.size && lines[startIndex].isBlank()) {
            startIndex++
        }
        
        // 移除结尾的空行
        var endIndex = lines.size - 1
        while (endIndex >= startIndex && lines[endIndex].isBlank()) {
            endIndex--
        }
        
        // 重构结果，保持所有内部格式
        return if (startIndex <= endIndex) {
            lines.subList(startIndex, endIndex + 1).joinToString("\n")
        } else {
            "" // 如果全是空行
        }
    }
}
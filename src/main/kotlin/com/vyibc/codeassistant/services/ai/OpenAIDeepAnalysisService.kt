package com.vyibc.codeassistant.services.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vyibc.codeassistant.config.AIConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAIDeepAnalysisService(private val config: AIConfig) : DeepAnalysisAIService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS) // 深度分析需要更多时间
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
        
    private val baseUrl = "https://api.openai.com/v1/chat/completions"
    
    private val systemPrompt = """
        你是代码分析专家。对提供的代码进行深度分析，为每行重要代码添加中文注释，解释其设计意图和实现原理。
        
        注释要求：
        1. 解释"为什么这样写"，不只是"做了什么"
        2. 说明技术选择的原因（如为什么使用deepcopy、为什么检查nullable等）
        3. 中文注释，简洁准确
        4. 保持原代码格式和结构
        
        示例：
        ```python
        # 使用 deepcopy 创建工具输入参数的深拷贝，以避免修改原始对象
        properties = deepcopy(tool.inputs)
        # 初始化空列表，用于存放必需参数的名称
        required = []
        # 遍历参数字典中的每一个参数
        for key, value in properties.items():
            # 将非标准的"any"类型转换为标准的"string"类型，确保JSON Schema兼容性
            if value["type"] == "any":
                value["type"] = "string"
            # 如果参数不是可选的(nullable)，则将其标记为必需参数
            if not ("nullable" in value and value["nullable"]):
                required.append(key)
        ```
        
        直接输出带注释的代码，不要任何额外说明。
    """.trimIndent()
    
    override fun deepAnalyzeCode(code: String): String {
        if (config.openaiApiKey.isBlank()) {
            throw IOException("OpenAI API Key未配置")
        }
        
        try {
            return requestDeepAnalysis(code)
        } catch (e: Exception) {
            throw IOException("AI深度分析失败: ${e.message}")
        }
    }
    
    private fun requestDeepAnalysis(code: String): String {
        val messages = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", systemPrompt)
            })
            add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", """
                    对以下代码添加中文注释，解释设计意图和实现原理：
                    
                    ```
                    $code
                    ```
                """.trimIndent())
            })
        }
        
        val json = JsonObject().apply {
            addProperty("model", config.openaiModel)
            add("messages", messages)
            addProperty("temperature", 0.3) // 较低温度确保分析准确性
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
     * 智能清理代码块响应，保持内部格式和缩进 - 复用基础翻译的逻辑
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
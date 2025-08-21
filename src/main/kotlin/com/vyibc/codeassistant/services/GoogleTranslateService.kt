package com.vyibc.codeassistant.services

import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

class GoogleTranslateService {
    
    private val client = OkHttpClient()
    private val baseUrl = "https://translate.googleapis.com/translate_a/single"
    
    /**
     * 使用Google翻译API进行文本翻译
     * @param text 要翻译的文本
     * @param sourceLang 源语言 (auto为自动检测)
     * @param targetLang 目标语言 (zh为中文, en为英文)
     */
    fun translate(
        text: String, 
        sourceLang: String = "auto", 
        targetLang: String = "zh"
    ): String {
        if (text.isBlank()) return text
        
        // 检测如果文本主要是中文则翻译为英文
        val actualTargetLang = if (containsChinese(text) && targetLang == "zh") "en" else targetLang
        
        return try {
            translateWithGoogleAPI(text, sourceLang, actualTargetLang)
        } catch (e: Exception) {
            // 如果谷歌翻译失败，返回原文
            "翻译失败: ${e.message}"
        }
    }
    
    private fun translateWithGoogleAPI(text: String, sourceLang: String, targetLang: String): String {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = "${baseUrl}?client=gtx&sl=${sourceLang}&tl=${targetLang}&dt=t&q=${encodedText}"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("翻译请求失败: ${response.code}")
        }
        
        val responseBody = response.body?.string() ?: throw IOException("响应体为空")
        return parseGoogleTranslateResponse(responseBody)
    }
    
    private fun parseGoogleTranslateResponse(jsonResponse: String): String {
        try {
            val jsonElement = JsonParser.parseString(jsonResponse)
            val jsonArray = jsonElement.asJsonArray
            
            val translationsArray = jsonArray[0].asJsonArray
            val result = StringBuilder()
            
            for (i in 0 until translationsArray.size()) {
                val translation = translationsArray[i].asJsonArray
                result.append(translation[0].asString)
            }
            
            return result.toString()
        } catch (e: Exception) {
            throw IOException("解析翻译结果失败: ${e.message}")
        }
    }
    
    private fun containsChinese(text: String): Boolean {
        return text.any { char ->
            char.code in 0x4E00..0x9FFF // 中文字符范围
        }
    }
}
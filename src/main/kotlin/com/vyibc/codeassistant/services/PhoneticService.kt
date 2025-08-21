package com.vyibc.codeassistant.services

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class PhoneticService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * 获取单词的音标
     * 使用Free Dictionary API
     */
    fun getPhonetic(word: String): String? {
        if (word.isBlank()) return null
        
        return try {
            val cleanWord = word.trim().lowercase().replace(Regex("[^a-zA-Z]"), "")
            if (cleanWord.isEmpty()) return null
            
            requestPhonetic(cleanWord)
        } catch (e: Exception) {
            null // 静默失败，返回null
        }
    }
    
    private fun requestPhonetic(word: String): String? {
        val url = "https://api.dictionaryapi.dev/api/v2/entries/en/$word"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "CodeAssistant-Plugin")
            .build()
            
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            return null
        }
        
        val responseBody = response.body?.string() ?: return null
        return parsePhoneticResponse(responseBody)
    }
    
    private fun parsePhoneticResponse(jsonResponse: String): String? {
        return try {
            val jsonArray = JsonParser.parseString(jsonResponse).asJsonArray
            if (jsonArray.size() == 0) return null
            
            val firstEntry = jsonArray[0].asJsonObject
            val phonetics = firstEntry.getAsJsonArray("phonetics")
            
            if (phonetics != null && phonetics.size() > 0) {
                // 优先查找美式音标 (US)
                for (i in 0 until phonetics.size()) {
                    val phonetic = phonetics[i].asJsonObject
                    val text = phonetic.get("text")?.asString
                    val audio = phonetic.get("audio")?.asString
                    
                    if (!text.isNullOrBlank() && !audio.isNullOrBlank()) {
                        // 如果音频URL包含US，优先使用
                        if (audio.contains("us.", ignoreCase = true)) {
                            return cleanPhonetic(text)
                        }
                    }
                }
                
                // 如果没有找到美式音标，使用第一个有效音标
                for (i in 0 until phonetics.size()) {
                    val phonetic = phonetics[i].asJsonObject
                    val text = phonetic.get("text")?.asString
                    if (!text.isNullOrBlank()) {
                        return cleanPhonetic(text)
                    }
                }
            }
            
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 清理和标准化音标 - 优化版本
     */
    private fun cleanPhonetic(phonetic: String): String {
        return phonetic
            // 基础清理
            .replace("ɹ", "r")  // 将 ɹ 替换为更常见的 r
            .replace("ə", "ə")  // 保持 schwa
            .replace(".ə", "ə") // 移除多余的点
            .replace("ʃ.", "ʃ") // 清理 ʃ 后的点
            .replace(".n", "n") // 移除 n 前的点
            
            // 常见元音优化（更符合标准美式发音）
            .replace("ɛ", "e")  // 将 ɛ 替换为更常见的 e (如level: /ˈlɛvəl/ → /ˈlevəl/)
            .replace("æ", "a")  // 将 æ 替换为 a
            .replace("ɪ", "i")  // 将 ɪ 替换为 i (某些情况下)
            .replace("ʊ", "u")  // 将 ʊ 替换为 u
            
            // 词尾优化
            .replace("ɛʃ.ən", "eʃn") // 特殊处理 -tion 结尾
            .replace("ɛʃən", "eʃn")  // 另一种 -tion 形式
            .replace("eɪʃən", "eʃn") // -ation 结尾
            .replace("ʃən", "ʃn")    // 简化 -tion 结尾
            
            // 辅音优化  
            .replace("ʧ", "tʃ")  // ch 音统一
            .replace("ʤ", "dʒ")  // j 音统一
            .replace("θ", "θ")   // th 音保持
            .replace("ð", "ð")   // th 音保持
            
            // 重音符号清理
            .replace("ˈ", "'")   // 主重音符号标准化（可选）
            .replace("ˌ", "")    // 移除次重音符号（简化）
            
            // 音节分隔符清理
            .replace(".", "")    // 移除音节点（根据需要）
            
            .trim()
    }
    
    /**
     * 为文本中的英文单词添加音标
     */
    fun addPhoneticsToText(text: String): String {
        val words = text.split(Regex("\\s+"))
        val phoneticWords = mutableListOf<String>()
        
        for (word in words) {
            if (word.matches(Regex("[a-zA-Z]+"))) {
                val phonetic = getPhonetic(word)
                if (phonetic != null) {
                    phoneticWords.add("$word: $phonetic")
                } else {
                    phoneticWords.add(word)
                }
            } else {
                phoneticWords.add(word)
            }
        }
        
        return if (phoneticWords.isNotEmpty()) {
            "// 音标: ${phoneticWords.joinToString(", ")}"
        } else {
            "// 未找到可标注音标的单词"
        }
    }
}
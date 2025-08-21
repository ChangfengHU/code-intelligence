package com.vyibc.codeassistant.services

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vyibc.codeassistant.settings.CodeAssistantSettings
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class AIPhoneticService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    /**
     * 使用AI生成英文到中文的高保真谐音翻译
     */
    fun generatePhoneticAssist(text: String): String {
        if (text.isBlank()) return "// 未提供文本"
        
        return try {
            val settings = CodeAssistantSettings.getInstance()
            val aiConfig = settings.getAIConfig()
            
            if (aiConfig.openaiApiKey.isBlank()) {
                return "// 请先配置OpenAI API Key"
            }
            
            val aiResponse = requestAIPhonetic(text, aiConfig.openaiApiKey)
            
            if (aiResponse.isNullOrBlank()) {
                // 如果AI失败，回退到简单处理
                return generateSimplePhonetic(text)
            }
            
            // 格式化AI响应
            formatAIResponse(aiResponse)
        } catch (e: Exception) {
            // 出错时回退到简单处理
            generateSimplePhonetic(text)
        }
    }
    
    private fun requestAIPhonetic(text: String, apiKey: String): String? {
        val systemPrompt = """
你是一位顶级的语音转写专家（Phonetic Transliteration Specialist），拥有语言学博士学位。你精通英语（特别是美式标准发音）和现代标准汉语的语音系统及国际音标（IPA）。

# 核心目标 (Objective)
根据用户提供的任何英文文本，生成一个纯粹基于发音的、在语音学上最接近原文读音的中文谐音翻译。你的工作是声音的搬运工，而非意义的传递者。

# 核心规则与约束 (Core Rules & Constraints)
1. **绝对音译至上**: 严格遵循源语言的发音。在选择汉字时，唯一标准是其普通话读音与英文音节的匹配度。
2. **摒弃语义**: 完全忽略所选汉字的原始词义。翻译结果在语义上应该是无意义的，其唯一价值在于声音的相似性。
3. **音节级保真**: 对每一个英文音节进行细致音译，确保不遗漏。
4. **采用标准发音**: 使用美式英语（General American English）的主流发音作为基准。
5. **简洁优先**: 优先选择简洁、常见的汉字组合，避免过于复杂的谐音。

# 特别注意事项
- **常用词简化**: 对于like "the", "a", "is", "and"等超高频词汇，使用最简洁的单音节谐音
- **避免过度拼接**: 不要为了追求"完美"而生成过长的谐音，如"the"用"德"而非"特何伊"
- **重音优先**: 重点匹配重读音节，轻读音节可适当简化

# 输出要求
请只输出最终的中文谐音结果，不要输出其他说明文字。格式示例：
the → 德
model → 马德尔
function → 方克申
        """.trimIndent()
        
        val userPrompt = "请为以下英文文本生成简洁准确的高保真中文谐音：\"$text\""
        
        val messages = JsonObject().apply {
            add("messages", JsonParser.parseString("""
                [
                    {"role": "system", "content": "${systemPrompt.replace("\"", "\\\"")}"},
                    {"role": "user", "content": "$userPrompt"}
                ]
            """).asJsonArray)
            addProperty("model", "gpt-4")
            addProperty("temperature", 0.3)
            addProperty("max_tokens", 1000)
        }
        
        val requestBody = messages.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()
            
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("AI API请求失败 (${response.code}): ${response.body?.string()}")
        }
        
        val responseBody = response.body?.string() ?: throw IOException("响应体为空")
        return parseAIResponse(responseBody)
    }
    
    private fun parseAIResponse(jsonResponse: String): String? {
        return try {
            val json = JsonParser.parseString(jsonResponse).asJsonObject
            val choices = json.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val firstChoice = choices[0].asJsonObject
                val message = firstChoice.getAsJsonObject("message")
                message.get("content")?.asString
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun formatAIResponse(aiResponse: String): String {
        // 简化的响应解析，直接提取中文谐音内容
        val lines = aiResponse.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        // 查找包含箭头(→)的行，这是新格式的标志
        val phoneticLines = lines.filter { it.contains("→") }
        
        if (phoneticLines.isNotEmpty()) {
            // 提取谐音部分
            val phoneticResults = phoneticLines.mapNotNull { line ->
                val parts = line.split("→")
                if (parts.size >= 2) {
                    val word = parts[0].trim()
                    val phonetic = parts[1].trim()
                    "$word→$phonetic"
                } else null
            }
            
            if (phoneticResults.isNotEmpty()) {
                return "// 高保真谐音: ${phoneticResults.joinToString(", ")}"
            }
        }
        
        // 如果没有找到标准格式，查找纯中文内容
        val chineseContent = extractChineseContent(aiResponse)
        if (chineseContent.isNotEmpty()) {
            return "// 高保真谐音: $chineseContent"
        }
        
        // 最后的备选：取第一个有意义的行
        val meaningfulLine = lines.firstOrNull { line ->
            line.length > 3 && !line.startsWith("**") && !line.startsWith("#")
        }
        
        return if (meaningfulLine != null) {
            "// 高保真谐音: $meaningfulLine"
        } else {
            "// 高保真谐音: 生成中..."
        }
    }
    
    /**
     * 从响应中提取中文内容
     */
    private fun extractChineseContent(text: String): String {
        // 匹配中文字符和常用符号 - 简化版本
        val chinesePattern = Regex("[\\u4e00-\\u9fff]+")
        val matches = chinesePattern.findAll(text)
        
        val chineseTexts = matches.map { it.value.trim() }
            .filter { it.isNotEmpty() && it.length > 1 }
            .take(5)
            .toList()
        
        return chineseTexts.joinToString(" ")
    }
    
    /**
     * 智能回退方案：即使AI失败也要提供谐音辅助
     */
    private fun generateSimplePhonetic(text: String): String {
        val enhancedMap = mapOf(
            // 更准确的编程词汇谐音
            "function" to "方克申",
            "class" to "克拉斯", 
            "public" to "帕布利克",
            "private" to "普赖维特",
            "static" to "斯塔提克",
            "void" to "沃伊德",
            "main" to "梅恩",
            "string" to "斯特林",
            "int" to "因特",
            "double" to "达布尔",
            "boolean" to "布利安",
            "array" to "阿瑞",
            "list" to "里斯特",
            "map" to "马普",
            "set" to "塞特",
            "return" to "瑞特恩",
            "if" to "伊夫",
            "else" to "埃尔斯",
            "for" to "福尔",
            "while" to "瓦伊尔",
            "do" to "度",
            "try" to "踹",
            "catch" to "卡奇",
            "finally" to "法伊纳利",
            "throw" to "思劳",
            "new" to "牛",
            "this" to "迪斯",
            "super" to "苏帕",
            "interface" to "因特费斯",
            "extends" to "伊克斯滕兹",
            "implements" to "因普利门茨",
            "package" to "帕基奇",
            "import" to "因颇特",
            "export" to "伊克斯颇特",
            "const" to "康斯特",
            "let" to "莱特",
            "var" to "瓦尔",
            "null" to "纳尔",
            "undefined" to "安迪法因德",
            "true" to "楚",
            "false" to "福尔斯",
            
            // 常用词汇 - 优化版本
            "the" to "德",           // /ðə/ 更简洁准确
            "a" to "阿",             // /ə/ 或 /eɪ/
            "an" to "安",            // /æn/
            "and" to "安德",         // /ænd/
            "or" to "欧",            // /ɔːr/
            "in" to "因",            // /ɪn/
            "on" to "昂",            // /ɒn/
            "at" to "艾特",          // /æt/
            "is" to "伊兹",          // /ɪz/
            "are" to "阿",           // /ɑːr/
            "was" to "沃兹",         // /wʌz/
            "were" to "沃",          // /wɜːr/
            "be" to "比",            // /biː/
            "to" to "图",            // /tuː/
            "of" to "奥夫",          // /ʌv/
            "for" to "福",           // /fɔːr/
            "with" to "维思",        // /wɪθ/
            "by" to "拜",            // /baɪ/
            "from" to "弗拉姆",      // /frʌm/
            "up" to "阿普",          // /ʌp/
            "down" to "当",          // /daʊn/
            "out" to "奥特",         // /aʊt/
            "about" to "阿保特",     // /əˈbaʊt/
            "into" to "因图",        // /ˈɪntuː/
            "over" to "欧沃",        // /ˈoʊvər/
            "after" to "阿夫特",     // /ˈæftər/
            "hello" to "哈洛",
            "world" to "沃尔德",
            "thank" to "散克",
            "you" to "语",
            "level" to "莱沃",
            "model" to "马德尔",     // /ˈmɑːdl/ 修正
            "create" to "克瑞艾特",
            "update" to "阿普戴特",
            "delete" to "迪利特",
            "select" to "瑟莱克特",
            "insert" to "因瑟特",
            "data" to "戴塔",
            "table" to "泰布尔",
            "database" to "戴塔贝斯",
            "server" to "瑟沃",
            "client" to "克莱恩特",
            "request" to "瑞奎斯特",
            "response" to "瑞斯庞斯",
            "service" to "瑟维斯",
            "method" to "迈索德",
            "parameter" to "帕拉米特",
            "argument" to "阿古门特",
            "variable" to "瓦瑞阿布尔",
            "constant" to "康斯坦特",
            "object" to "奥布杰克特",
            "property" to "普罗帕提",
            "value" to "瓦鲁",
            "key" to "基",
            "index" to "因戴克斯",
            "length" to "朗思",
            "size" to "赛兹",
            "name" to "内姆",
            "type" to "塔伊普",
            "file" to "法伊尔",
            "folder" to "佛尔德",
            "path" to "帕思",
            "url" to "尤阿尔",
            "api" to "艾皮艾",
            "http" to "艾奇提提皮",
            "https" to "艾奇提提皮艾斯",
            "json" to "杰森",
            "xml" to "艾克斯艾姆艾尔",
            "html" to "艾奇提艾姆艾尔",
            "css" to "西艾斯艾斯"
        )
        
        val words = text.split(Regex("\\s+"))
            .map { it.replace(Regex("[^a-zA-Z]"), "") }
            .filter { it.isNotEmpty() }
        
        if (words.isEmpty()) {
            return "// 高保真谐音: 无可处理内容"
        }
        
        val phoneticResults = mutableListOf<String>()
        val unmatchedWords = mutableListOf<String>()
        
        for (word in words) {
            val cleanWord = word.lowercase()
            val phonetic = enhancedMap[cleanWord]
            if (phonetic != null) {
                phoneticResults.add("$word→$phonetic")
            } else {
                unmatchedWords.add(word)
            }
        }
        
        // 为没有匹配的单词生成基础谐音
        for (word in unmatchedWords) {
            val basicPhonetic = generateBasicPhonetic(word)
            phoneticResults.add("$word→$basicPhonetic")
        }
        
        return "// 高保真谐音: ${phoneticResults.joinToString(", ")}"
    }
    
    /**
     * 为任意英文单词生成基础谐音（音节拆分方式）
     */
    private fun generateBasicPhonetic(word: String): String {
        val syllableMap = mapOf(
            // 常见音节映射
            "a" to "阿", "e" to "伊", "i" to "艾", "o" to "欧", "u" to "尤",
            "al" to "奥", "ar" to "阿", "er" to "尔", "or" to "奥", "ur" to "尔",
            "an" to "安", "en" to "恩", "in" to "因", "on" to "昂", "un" to "昂",
            "at" to "艾特", "et" to "埃特", "it" to "伊特", "ot" to "欧特", "ut" to "阿特",
            "ac" to "艾克", "ec" to "艾克", "ic" to "伊克", "oc" to "奥克", "uc" to "阿克",
            "ap" to "艾普", "ep" to "艾普", "ip" to "伊普", "op" to "奥普", "up" to "阿普",
            "ab" to "艾布", "eb" to "艾布", "ib" to "伊布", "ob" to "奥布", "ub" to "阿布",
            
            // 辅音开头
            "b" to "布", "c" to "克", "d" to "德", "f" to "夫", "g" to "格",
            "h" to "何", "j" to "杰", "k" to "克", "l" to "尔", "m" to "姆",
            "n" to "恩", "p" to "普", "q" to "奎", "r" to "尔", "s" to "斯",
            "t" to "特", "v" to "维", "w" to "沃", "x" to "克斯", "y" to "伊", "z" to "兹",
            
            // 常见结尾
            "ed" to "德", "ing" to "因", "er" to "尔", "est" to "埃斯特",
            "ly" to "利", "ty" to "提", "ry" to "瑞", "sy" to "西"
        )
        
        if (word.length <= 2) {
            return syllableMap[word.lowercase()] ?: "未知"
        }
        
        // 简单的音节拆分逻辑
        val result = StringBuilder()
        var i = 0
        while (i < word.length) {
            var found = false
            
            // 尝试匹配2-3字母的音节
            for (len in 3 downTo 2) {
                if (i + len <= word.length) {
                    val segment = word.substring(i, i + len).lowercase()
                    if (syllableMap.containsKey(segment)) {
                        result.append(syllableMap[segment])
                        i += len
                        found = true
                        break
                    }
                }
            }
            
            // 没找到匹配的，处理单字母
            if (!found) {
                val char = word[i].lowercase()
                result.append(syllableMap[char] ?: char)
                i++
            }
        }
        
        return result.toString()
    }
}
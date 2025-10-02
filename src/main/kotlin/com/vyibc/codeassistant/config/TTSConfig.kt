package com.vyibc.codeassistant.config

enum class TTSProvider {
    OPENAI("OpenAI TTS", "高质量神经网络语音"),
    AZURE("Azure TTS", "微软认知服务语音"),
    GOOGLE("Google TTS", "谷歌云语音合成"),
    DEEPSEEK("DeepSeek TTS", "DeepSeek语音合成"),
    QWEN("阿里千问 TTS", "通义千问语音合成"),
    GEMINI("Gemini TTS", "Gemini语音合成"),
    SYSTEM("系统TTS", "本地系统语音合成");
    
    val displayName: String
    val description: String
    
    constructor(displayName: String, description: String) {
        this.displayName = displayName
        this.description = description
    }
}

enum class OpenAIVoice(val voiceName: String, val description: String) {
    ALLOY("alloy", "中性，平衡"),
    ECHO("echo", "男性，自然"),
    FABLE("fable", "男性，戏剧化"),
    ONYX("onyx", "男性，深沉"),
    NOVA("nova", "女性，年轻"),
    SHIMMER("shimmer", "女性，温和")
}

data class TTSConfig(
    var provider: TTSProvider = TTSProvider.OPENAI,
    var openaiApiKey: String = "",
    var openaiVoice: OpenAIVoice = OpenAIVoice.ALLOY,
    var azureApiKey: String = "",
    var azureRegion: String = "eastus",
    var googleApiKey: String = "",
    var deepseekApiKey: String = "",
    var qwenApiKey: String = "",
    var geminiApiKey: String = "",
    var speed: Float = 1.0f,
    var volume: Float = 1.0f
)

data class AIConfig(
    var provider: AIProvider = AIProvider.OPENAI,
    var openaiApiKey: String = "",
    var openaiModel: String = "gpt-3.5-turbo",
    var azureOpenaiApiKey: String = "",
    var azureOpenaiEndpoint: String = "",
    var claudeApiKey: String = "",
    var geminiApiKey: String = "",
    var geminiModel: String = "gemini-1.5-pro",
    var deepseekApiKey: String = "",
    var deepseekModel: String = "deepseek-chat",
    var qwenApiKey: String = "",
    var qwenModel: String = "qwen-turbo",
    var temperature: Float = 0.3f,
    var maxTokens: Int = 2000
)

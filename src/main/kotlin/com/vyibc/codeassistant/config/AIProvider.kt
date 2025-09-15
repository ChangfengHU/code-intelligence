package com.vyibc.codeassistant.config

enum class AIProvider(val displayName: String, val description: String) {
    OPENAI("OpenAI GPT", "OpenAI ChatGPT系列模型"),
    AZURE_OPENAI("Azure OpenAI", "微软Azure OpenAI服务"),
    CLAUDE("Claude", "Anthropic Claude模型"),
    GEMINI("Google Gemini", "谷歌Gemini模型"),
    DEEPSEEK("DeepSeek", "DeepSeek AI模型"),
    QWEN("阿里千问", "阿里巴巴通义千问模型");
}

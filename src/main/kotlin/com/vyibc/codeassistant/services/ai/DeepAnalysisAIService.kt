package com.vyibc.codeassistant.services.ai

interface DeepAnalysisAIService {
    /**
     * 对代码进行深度分析，添加详细的逻辑解释和作用说明
     */
    fun deepAnalyzeCode(code: String): String
}
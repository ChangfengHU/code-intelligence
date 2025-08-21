package com.vyibc.codeassistant.services.ai

interface AIService {
    fun translateCode(code: String): String
}
package com.vyibc.codeassistant.services.tts

import com.google.gson.JsonObject
import com.vyibc.codeassistant.config.TTSConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.sound.sampled.*

class GeminiTTSService(private val config: TTSConfig) : TTSService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
        
    private val baseUrl = "https://texttospeech.googleapis.com/v1/text:synthesize"
    
    override fun speak(text: String) {
        if (config.geminiApiKey.isBlank()) {
            throw IOException("Gemini API Key未配置")
        }
        
        try {
            val audioData = requestTTS(text)
            playAudio(audioData)
        } catch (e: Exception) {
            throw IOException("Gemini TTS失败: ${e.message}")
        }
    }
    
    /**
     * 获取TTS音频数据（不播放）
     */
    fun getTTSAudioData(text: String): ByteArray {
        if (config.geminiApiKey.isBlank()) {
            throw IOException("Gemini API Key未配置")
        }
        
        try {
            return requestTTS(text)
        } catch (e: Exception) {
            throw IOException("Gemini TTS失败: ${e.message}")
        }
    }
    
    private fun requestTTS(text: String): ByteArray {
        val input = JsonObject().apply {
            addProperty("text", text)
        }
        
        val voice = JsonObject().apply {
            addProperty("languageCode", "zh-CN")
            addProperty("name", "zh-CN-Wavenet-A") // 使用中文女声
            addProperty("ssmlGender", "FEMALE")
        }
        
        val audioConfig = JsonObject().apply {
            addProperty("audioEncoding", "MP3")
            addProperty("speakingRate", config.speed)
            addProperty("volumeGainDb", (config.volume * 16 - 16).toDouble()) // 转换为dB
        }
        
        val json = JsonObject().apply {
            add("input", input)
            add("voice", voice)
            add("audioConfig", audioConfig)
        }
        
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("$baseUrl?key=${config.geminiApiKey}")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()
            
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "未知错误"
            throw IOException("Gemini API请求失败 (${response.code}): $errorBody")
        }
        
        val responseBody = response.body?.string() ?: throw IOException("响应体为空")
        return parseGeminiTTSResponse(responseBody)
    }
    
    private fun parseGeminiTTSResponse(jsonResponse: String): ByteArray {
        try {
            val responseJson = com.google.gson.JsonParser.parseString(jsonResponse).asJsonObject
            val audioContent = responseJson.get("audioContent").asString
            
            // Google TTS返回的是base64编码的音频数据
            return java.util.Base64.getDecoder().decode(audioContent)
            
        } catch (e: Exception) {
            throw IOException("解析Gemini TTS响应失败: ${e.message}")
        }
    }
    
    private fun playAudio(audioData: ByteArray) {
        try {
            // 创建临时音频文件
            val tempFile = File.createTempFile("gemini_tts_", ".mp3")
            tempFile.deleteOnExit()
            tempFile.writeBytes(audioData)
            
            // 播放音频
            playAudioFile(tempFile)
            
        } catch (e: Exception) {
            throw IOException("音频播放失败: ${e.message}")
        }
    }
    
    private fun playAudioFile(audioFile: File) {
        try {
            // 使用系统播放器播放MP3文件
            val osName = System.getProperty("os.name").lowercase()
            
            when {
                osName.contains("mac") -> {
                    val process = ProcessBuilder("afplay", audioFile.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    process.waitFor()
                }
                osName.contains("windows") -> {
                    val process = ProcessBuilder(
                        "powershell", "-Command",
                        "Add-Type -AssemblyName presentationCore; " +
                        "\$mediaPlayer = New-Object system.windows.media.mediaplayer; " +
                        "\$mediaPlayer.open('${audioFile.absolutePath}'); " +
                        "\$mediaPlayer.Play(); " +
                        "Start-Sleep -Seconds 10"
                    ).redirectErrorStream(true).start()
                    process.waitFor()
                }
                osName.contains("linux") -> {
                    // 尝试使用不同的播放器
                    val players = listOf("mpg123", "mplayer", "vlc", "ffplay")
                    var played = false
                    
                    for (player in players) {
                        if (isCommandAvailable(player)) {
                            val process = ProcessBuilder(player, audioFile.absolutePath)
                                .redirectErrorStream(true)
                                .start()
                            process.waitFor()
                            played = true
                            break
                        }
                    }
                    
                    if (!played) {
                        throw IOException("Linux系统需要安装音频播放器 (mpg123, mplayer, vlc 或 ffplay)")
                    }
                }
                else -> {
                    throw IOException("不支持的操作系统: $osName")
                }
            }
        } catch (e: Exception) {
            throw IOException("音频文件播放失败: ${e.message}")
        }
    }
    
    private fun isCommandAvailable(command: String): Boolean {
        return try {
            val process = ProcessBuilder("which", command).start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
package com.vyibc.codeassistant.services.tts

import com.vyibc.codeassistant.config.TTSConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class AzureTTSService(private val config: TTSConfig) : TTSService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    override fun speak(text: String) {
        if (config.azureApiKey.isBlank()) {
            throw IOException("Azure API Key未配置")
        }
        
        try {
            val audioData = requestTTS(text)
            playAudio(audioData)
        } catch (e: Exception) {
            throw IOException("Azure TTS失败: ${e.message}")
        }
    }
    
    private fun requestTTS(text: String): ByteArray {
        val ssml = buildSSML(text)
        val requestBody = ssml.toRequestBody("application/ssml+xml".toMediaType())
        
        val baseUrl = "https://${config.azureRegion}.tts.speech.microsoft.com/cognitiveservices/v1"
        
        val request = Request.Builder()
            .url(baseUrl)
            .header("Ocp-Apim-Subscription-Key", config.azureApiKey)
            .header("Content-Type", "application/ssml+xml")
            .header("X-Microsoft-OutputFormat", "audio-16khz-128kbitrate-mono-mp3")
            .header("User-Agent", "CodeAssistant")
            .post(requestBody)
            .build()
            
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "未知错误"
            throw IOException("Azure API请求失败 (${response.code}): $errorBody")
        }
        
        return response.body?.bytes() ?: throw IOException("响应体为空")
    }
    
    private fun buildSSML(text: String): String {
        val rate = when {
            config.speed < 0.8f -> "slow"
            config.speed > 1.2f -> "fast"
            else -> "medium"
        }
        
        return """<?xml version="1.0" encoding="UTF-8"?>
            <speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="en-US">
                <voice name="en-US-JennyNeural">
                    <prosody rate="$rate" volume="${(config.volume * 100).toInt()}%">
                        $text
                    </prosody>
                </voice>
            </speak>""".trimIndent()
    }
    
    private fun playAudio(audioData: ByteArray) {
        try {
            val tempFile = File.createTempFile("azure_tts_", ".mp3")
            tempFile.deleteOnExit()
            tempFile.writeBytes(audioData)
            
            playAudioFile(tempFile)
            
        } catch (e: Exception) {
            throw IOException("音频播放失败: ${e.message}")
        }
    }
    
    private fun playAudioFile(audioFile: File) {
        val osName = System.getProperty("os.name").lowercase()
        
        when {
            osName.contains("mac") -> {
                ProcessBuilder("afplay", audioFile.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor()
            }
            osName.contains("windows") -> {
                ProcessBuilder(
                    "powershell", "-Command",
                    "Add-Type -AssemblyName presentationCore; " +
                    "\$mediaPlayer = New-Object system.windows.media.mediaplayer; " +
                    "\$mediaPlayer.open('${audioFile.absolutePath}'); " +
                    "\$mediaPlayer.Play(); " +
                    "Start-Sleep -Seconds 10"
                ).redirectErrorStream(true)
                    .start()
                    .waitFor()
            }
            osName.contains("linux") -> {
                val players = listOf("mpg123", "mplayer", "vlc", "ffplay")
                var played = false
                
                for (player in players) {
                    if (isCommandAvailable(player)) {
                        ProcessBuilder(player, audioFile.absolutePath)
                            .redirectErrorStream(true)
                            .start()
                            .waitFor()
                        played = true
                        break
                    }
                }
                
                if (!played) {
                    throw IOException("需要安装音频播放器")
                }
            }
        }
    }
    
    private fun isCommandAvailable(command: String): Boolean {
        return try {
            ProcessBuilder("which", command).start().waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
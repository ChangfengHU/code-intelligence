package com.vyibc.codeassistant.services.tts

import com.google.gson.JsonObject
import com.vyibc.codeassistant.config.TTSConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.Base64

class GoogleTTSService(private val config: TTSConfig) : TTSService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
        
    private val baseUrl = "https://texttospeech.googleapis.com/v1/text:synthesize"
    
    override fun speak(text: String) {
        if (config.googleApiKey.isBlank()) {
            throw IOException("Google API Key未配置")
        }
        
        try {
            val audioData = requestTTS(text)
            playAudio(audioData)
        } catch (e: Exception) {
            throw IOException("Google TTS失败: ${e.message}")
        }
    }
    
    private fun requestTTS(text: String): ByteArray {
        val json = JsonObject().apply {
            add("input", JsonObject().apply {
                addProperty("text", text)
            })
            add("voice", JsonObject().apply {
                addProperty("languageCode", "en-US")
                addProperty("name", "en-US-Standard-J") // 美式英语男声
                addProperty("ssmlGender", "MALE")
            })
            add("audioConfig", JsonObject().apply {
                addProperty("audioEncoding", "MP3")
                addProperty("speakingRate", config.speed.toDouble())
                addProperty("volumeGainDb", (config.volume - 1.0f) * 16) // 转换为dB
            })
        }
        
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("$baseUrl?key=${config.googleApiKey}")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()
            
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "未知错误"
            throw IOException("Google API请求失败 (${response.code}): $errorBody")
        }
        
        val responseBody = response.body?.string() ?: throw IOException("响应体为空")
        val responseJson = com.google.gson.JsonParser.parseString(responseBody).asJsonObject
        val audioContent = responseJson.get("audioContent").asString
        
        return Base64.getDecoder().decode(audioContent)
    }
    
    private fun playAudio(audioData: ByteArray) {
        try {
            val tempFile = File.createTempFile("google_tts_", ".mp3")
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
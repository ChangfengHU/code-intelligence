package com.vyibc.codeassistant.services.tts

import com.vyibc.codeassistant.config.TTSConfig
import java.io.IOException

class SystemTTSService(private val config: TTSConfig) : TTSService {
    
    override fun speak(text: String) {
        if (text.isBlank()) return
        
        try {
            val osName = System.getProperty("os.name").lowercase()
            
            when {
                osName.contains("mac") -> speakOnMac(text)
                osName.contains("windows") -> speakOnWindows(text)
                osName.contains("linux") -> speakOnLinux(text)
                else -> throw IOException("不支持的操作系统: $osName")
            }
        } catch (e: Exception) {
            throw IOException("系统TTS发音失败: ${e.message}")
        }
    }
    
    private fun speakOnMac(text: String) {
        // 使用更好的美式英语语音
        val voices = listOf("Samantha", "Alex", "Victoria", "Tom")
        val selectedVoice = voices.firstOrNull { isVoiceAvailable(it) } ?: "Alex"
        
        val rate = (200 * config.speed).toInt() // 默认语速大约200词/分钟
        val command = arrayOf(
            "say", 
            "-v", selectedVoice,
            "-r", rate.toString(),
            text
        )
        
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
            
        // 同步等待完成，避免并发问题
        process.waitFor()
    }
    
    private fun speakOnWindows(text: String) {
        val escapedText = text.replace("\"", "\\\"")
        val rate = (config.speed - 1.0f) * 10 // Windows语速范围 -10 到 +10
        
        val command = arrayOf(
            "powershell", "-Command",
            "Add-Type -AssemblyName System.Speech; " +
            "\$speak = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
            "\$speak.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::Female, [System.Speech.Synthesis.VoiceAge]::Adult, 0, [System.Globalization.CultureInfo]'en-US'); " +
            "\$speak.Rate = $rate; " +
            "\$speak.Volume = ${(config.volume * 100).toInt()}; " +
            "\$speak.Speak('$escapedText')"
        )
        
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
            
        process.waitFor()
    }
    
    private fun speakOnLinux(text: String) {
        val speed = (config.speed * 175).toInt() // espeak语速，默认175词/分钟
        
        if (isCommandAvailable("espeak")) {
            val command = arrayOf(
                "espeak", 
                "-v", "en-us",
                "-s", speed.toString(),
                "-a", (config.volume * 100).toInt().toString(),
                text
            )
            
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
                
            process.waitFor()
        } else if (isCommandAvailable("festival")) {
            val command = arrayOf("sh", "-c", "echo '$text' | festival --tts")
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
                
            process.waitFor()
        } else {
            throw IOException("Linux系统需要安装 espeak 或 festival")
        }
    }
    
    private fun isVoiceAvailable(voiceName: String): Boolean {
        return try {
            val process = ProcessBuilder("say", "-v", "?").start()
            val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
            process.waitFor()
            output.contains(voiceName)
        } catch (e: Exception) {
            false
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
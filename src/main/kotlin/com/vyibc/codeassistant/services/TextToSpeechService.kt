package com.vyibc.codeassistant.services

import com.vyibc.codeassistant.config.TTSProvider
import com.vyibc.codeassistant.services.tts.*
import com.vyibc.codeassistant.settings.CodeAssistantSettings
import java.io.File
import java.io.IOException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TextToSpeechService {
    
    data class AudioTask(
        val order: Int,
        val text: String,
        val future: Future<ByteArray>
    )
    
    private val textQueue = LinkedBlockingQueue<String>()
    private val audioQueue = PriorityBlockingQueue<AudioTask>(10) { task1, task2 -> 
        task1.order.compareTo(task2.order)
    }
    private val isPlaying = AtomicBoolean(false)
    private val orderCounter = AtomicInteger(0)
    private val expectedPlayOrder = AtomicInteger(0)
    
    // 延迟初始化线程池，避免启动时就创建
    private var requestExecutor: ExecutorService? = null
    private fun getExecutor(): ExecutorService {
        if (requestExecutor?.isShutdown != false) {
            requestExecutor = Executors.newFixedThreadPool(3)
        }
        return requestExecutor!!
    }
    
    private var requestWorkerThread: Thread? = null
    private var playbackWorkerThread: Thread? = null
    
    /**
     * 使用配置的TTS服务进行英文发音（支持排队）
     * @param text 要发音的英文文本
     */
    fun speakEnglish(text: String) {
        if (text.isBlank()) return
        
        // 添加到文本队列
        textQueue.offer(text)
        
        // 启动工作线程（如果尚未启动）
        startWorkerThreads()
    }
    
    /**
     * 停止当前发音并清空队列
     */
    fun stopSpeaking() {
        textQueue.clear()
        audioQueue.clear()
        isPlaying.set(false)
        orderCounter.set(0)
        expectedPlayOrder.set(0)
        
        requestWorkerThread?.interrupt()
        playbackWorkerThread?.interrupt()
        
        // 取消所有进行中的请求
        requestExecutor?.shutdownNow()
    }
    
    private fun startWorkerThreads() {
        startRequestWorker()
        startPlaybackWorker()
    }
    
    /**
     * 请求工作线程：并行发送TTS API请求
     */
    private fun startRequestWorker() {
        if (requestWorkerThread?.isAlive == true) return
        
        requestWorkerThread = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val text = textQueue.take() // 阻塞等待新的文本
                    
                    if (text.isBlank()) continue
                    
                    val order = orderCounter.getAndIncrement()
                    
                    // 提交异步任务到线程池
                    val future = getExecutor().submit<ByteArray> {
                        try {
                            val ttsService = createTTSService()
                            // 修改TTS服务接口，让它返回音频数据而不是直接播放
                            requestTTSAudio(ttsService, text)
                        } catch (e: Exception) {
                            System.err.println("TTS API请求错误: ${e.message}")
                            ByteArray(0) // 返回空数组表示失败
                        }
                    }
                    
                    // 将任务添加到音频队列
                    audioQueue.offer(AudioTask(order, text, future))
                }
            } catch (e: InterruptedException) {
                // 线程被中断，正常退出
            }
        }.apply {
            isDaemon = true
            name = "TTS-Request-Worker"
        }
        
        requestWorkerThread?.start()
    }
    
    /**
     * 播放工作线程：按顺序播放音频
     */
    private fun startPlaybackWorker() {
        if (playbackWorkerThread?.isAlive == true) return
        
        playbackWorkerThread = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val audioTask = audioQueue.take() // 阻塞等待音频任务
                    
                    // 等待正确的播放顺序
                    while (audioTask.order != expectedPlayOrder.get()) {
                        Thread.sleep(50) // 短暂等待
                    }
                    
                    try {
                        isPlaying.set(true)
                        
                        // 等待API请求完成
                        val audioData = audioTask.future.get(30, TimeUnit.SECONDS)
                        
                        if (audioData.isNotEmpty()) {
                            playAudioData(audioData)
                        }
                        
                        expectedPlayOrder.incrementAndGet()
                        
                    } catch (e: TimeoutException) {
                        System.err.println("TTS请求超时: ${audioTask.text}")
                        expectedPlayOrder.incrementAndGet()
                    } catch (e: Exception) {
                        System.err.println("音频播放错误: ${e.message}")
                        expectedPlayOrder.incrementAndGet()
                    } finally {
                        isPlaying.set(false)
                        // 短暂停顿，避免连续发音太快
                        Thread.sleep(200)
                    }
                }
            } catch (e: InterruptedException) {
                // 线程被中断，正常退出
            }
        }.apply {
            isDaemon = true
            name = "TTS-Playback-Worker"
        }
        
        playbackWorkerThread?.start()
    }
    
    private fun createTTSService(): com.vyibc.codeassistant.services.tts.TTSService {
        val settings = CodeAssistantSettings.getInstance()
        val config = settings.getTTSConfig()
        
        return when (config.provider) {
            TTSProvider.OPENAI -> OpenAITTSService(config)
            TTSProvider.AZURE -> AzureTTSService(config)
            TTSProvider.GOOGLE -> GoogleTTSService(config)
            TTSProvider.SYSTEM -> SystemTTSService(config)
        }
    }
    
    /**
     * 请求TTS音频数据（不播放）
     */
    private fun requestTTSAudio(ttsService: com.vyibc.codeassistant.services.tts.TTSService, text: String): ByteArray {
        return when (ttsService) {
            is OpenAITTSService -> {
                // 使用新的getTTSAudioData方法
                ttsService.getTTSAudioData(text)
            }
            else -> {
                // 对于其他TTS服务，暂时使用旧的同步方法
                // 这些服务目前仍然会阻塞直到播放完成
                ttsService.speak(text)
                ByteArray(0)
            }
        }
    }
    
    /**
     * 从OpenAI TTS服务获取音频数据
     */
    private fun getTTSAudioData(ttsService: OpenAITTSService, text: String): ByteArray {
        // 这个方法已经不需要了，直接使用上面的requestTTSAudio方法
        return ttsService.getTTSAudioData(text)
    }
    
    /**
     * 播放音频数据
     */
    private fun playAudioData(audioData: ByteArray) {
        try {
            // 创建临时音频文件
            val tempFile = File.createTempFile("tts_audio_", ".mp3")
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
    
    /**
     * 检查是否正在发音
     */
    fun isSpeaking(): Boolean = isPlaying.get()
    
    /**
     * 获取队列中待发音的文本数量
     */
    fun getQueueSize(): Int = textQueue.size + audioQueue.size
}
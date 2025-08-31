# 代码问答助手技术方案 - 完善版

## 1. 功能概述

### 1.1 核心功能
- **一级菜单**：代码对话
- **二级菜单**：问答助手
- **智能分析**：针对框选代码进行深度分析，结合PSI获取上下文信息
- **多轮对话**：支持连续问答，保持上下文记忆
- **会话管理**：按类全限定路径管理会话，支持历史记录
- **首次默认问答**：启动时自动进行一次代码分析
- **API Key配置**：支持在设置页面配置OpenAI API Key
- **深度PSI分析**：分析当前类的其他未框选代码、调用链路的其他类的方法

### 1.2 技术特性
- 使用PSI API获取代码结构和调用链信息
- 支持系统提示词后台配置
- 本地持久化存储会话历史
- 可配置的会话和消息数量限制（默认100个会话，50次问答）
- 基于类全限定路径的会话管理
- 支持首次启动自动分析框选代码

## 2. 架构设计

### 2.1 整体架构
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   UI Layer      │    │  Service Layer  │    │ Storage Layer   │
│                 │    │                 │    │                 │
│ - 问答对话框    │◄──►│ - 问答服务      │◄──►│ - 会话存储      │
│ - 会话历史面板  │    │ - PSI分析服务   │    │ - 配置存储      │
│ - 配置界面      │    │ - AI对话服务    │    │ - 消息存储      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### 2.2 核心模块

#### 2.2.1 UI层
- `CodeChatDialog`: 主要问答对话框
- `SessionHistoryPanel`: 会话历史面板
- `MessageComponent`: 单条消息显示组件
- `ConfigPanel`: 问答助手配置面板

#### 2.2.2 服务层
- `CodeChatService`: 问答服务核心
- `PSIContextAnalyzer`: PSI代码上下文分析
- `SessionManager`: 会话管理服务
- `AIConversationService`: AI对话服务

#### 2.2.3 存储层
- `ChatSessionStorage`: 会话存储
- `ChatConfigStorage`: 配置存储
- `ChatHistoryStorage`: 消息历史存储

## 3. 数据模型

### 3.1 会话模型
```kotlin
data class ChatSession(
    val id: String,                    // 会话ID
    val className: String,             // 类全限定路径
    val filePath: String,              // 文件路径
    val createdAt: Long,               // 创建时间
    val lastActiveAt: Long,            // 最后活跃时间
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val metadata: Map<String, Any> = emptyMap()
)
```

### 3.2 消息模型
```kotlin
data class ChatMessage(
    val id: String,                    // 消息ID
    val type: MessageType,             // 消息类型
    val content: String,               // 消息内容
    val timestamp: Long,               // 时间戳
    val codeContext: CodeContext? = null,  // 代码上下文
    val metadata: Map<String, Any> = emptyMap()
)

enum class MessageType {
    USER,           // 用户消息
    ASSISTANT,      // AI助手消息
    SYSTEM,         // 系统消息
    CODE_ANALYSIS   // 代码分析消息
}
```

### 3.3 代码上下文模型
```kotlin
data class CodeContext(
    val selectedCode: String,          // 框选的代码
    val selectedRange: TextRange,      // 选中范围
    val className: String,             // 当前类名
    val methodName: String?,           // 当前方法名
    val classContext: String,          // 整个类的代码
    val imports: List<String>,         // 导入语句
    val dependencies: List<DependencyInfo>, // 依赖信息
    val callChain: List<CallInfo>      // 调用链信息
)

data class DependencyInfo(
    val className: String,             // 依赖类名
    val methods: List<String>,         // 相关方法
    val code: String                   // 相关代码片段
)

data class CallInfo(
    val className: String,             // 调用的类
    val methodName: String,            // 调用的方法
    val code: String                   // 方法代码
)
```

## 4. 核心功能实现

### 4.1 PSI上下文分析
```kotlin
class PSIContextAnalyzer {
    fun analyzeContext(
        file: PsiFile, 
        selectionRange: TextRange
    ): CodeContext {
        // 1. 获取选中代码
        // 2. 分析当前类和方法
        // 3. 获取imports和依赖
        // 4. 分析调用链
        // 5. 构建完整上下文
    }
    
    private fun findCallChain(element: PsiElement): List<CallInfo>
    private fun findDependencies(psiClass: PsiClass): List<DependencyInfo>
    private fun extractClassContext(psiClass: PsiClass): String
}
```

### 4.2 会话管理
```kotlin
class SessionManager {
    // 会话限制配置
    private var maxSessions: Int = 100
    private var maxMessagesPerSession: Int = 50
    
    fun getOrCreateSession(className: String, filePath: String): ChatSession
    fun addMessage(sessionId: String, message: ChatMessage)
    fun cleanupOldSessions()
    fun cleanupOldMessages(sessionId: String)
    fun getAllSessions(): List<ChatSession>
    fun deleteSession(sessionId: String)
}
```

### 4.3 AI对话服务
```kotlin
class AIConversationService {
    private var systemPrompt: String = getDefaultSystemPrompt()
    
    suspend fun sendMessage(
        message: String,
        codeContext: CodeContext,
        conversationHistory: List<ChatMessage>
    ): String {
        // 1. 构建完整的提示词
        // 2. 包含代码上下文
        // 3. 包含对话历史
        // 4. 发送到AI服务
        // 5. 处理响应
    }
    
    private fun buildContextPrompt(context: CodeContext): String
    private fun buildConversationHistory(messages: List<ChatMessage>): String
}
```

## 5. UI设计

### 5.1 主对话框
```
┌─────────────────────────────────────────────────┐
│ 代码问答助手 - MainActivity.java               │
├─────────────────────────────────────────────────┤
│ 📄 会话历史  🔧 设置                          │
├─────────────────────────────────────────────────┤
│                                                 │
│ 🤖 助手: 我正在分析你选中的代码...             │
│                                                 │
│ 👤 用户: 这个方法的作用是什么？                 │
│                                                 │
│ 🤖 助手: 这个方法主要用于...                   │
│                                                 │
│                                                 │
├─────────────────────────────────────────────────┤
│ 💬 请输入你的问题...              [发送] [清空] │
└─────────────────────────────────────────────────┘
```

### 5.2 会话历史面板
```
┌─────────────────────────────────────┐
│ 会话历史                            │
├─────────────────────────────────────┤
│ 📁 com.example.MainActivity         │
│    └─ 2024-08-27 15:30 (5条消息)  │
│                                     │
│ 📁 com.example.UserService          │
│    └─ 2024-08-27 14:20 (3条消息)  │
│                                     │
│ 📁 com.example.DataProcessor        │
│    └─ 2024-08-27 13:10 (8条消息)  │
└─────────────────────────────────────┘
```

## 6. 配置管理

### 6.1 配置项
```kotlin
data class ChatConfig(
    // AI配置
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val maxTokens: Int = 4000,
    val temperature: Double = 0.3,
    
    // 会话配置
    val maxSessions: Int = 100,
    val maxMessagesPerSession: Int = 50,
    val autoSaveInterval: Int = 30, // 秒
    
    // UI配置
    val showTimestamp: Boolean = true,
    val enableCodeHighlight: Boolean = true,
    val dialogWidth: Int = 800,
    val dialogHeight: Int = 600
)
```

### 6.2 默认系统提示词
```
你是一个专业的代码分析助手，擅长分析多种编程语言代码。请根据用户选中的代码和提供的上下文信息，进行深入分析和解答。

上下文信息包括：
1. 用户选中的代码片段
2. 当前文件的完整代码
3. 相关的导入语句（如果适用）
4. 依赖的类和方法（如果适用）
5. 调用链路信息（如果适用）

请以专业、准确、易懂的方式回答用户的问题。如果需要，可以：
- 解释代码的功能和实现原理
- 分析代码的设计模式和最佳实践
- 指出潜在的问题或改进建议
- 解释与其他模块的交互关系
- 提供代码优化建议

回答请使用中文，保持简洁明了。当用户首次选择代码时，请主动分析这段代码的作用和关键点。

支持的语言包括但不限于：Java, Kotlin, Python, JavaScript, TypeScript, Go, C/C++等。
```

## 7. 存储设计

### 7.1 文件结构
```
~/.code-assistant/
├── chat/
│   ├── sessions/
│   │   ├── session_001.json
│   │   ├── session_002.json
│   │   └── ...
│   ├── messages/
│   │   ├── session_001_messages.json
│   │   ├── session_002_messages.json
│   │   └── ...
│   └── index.json
├── config/
│   └── chat_config.json
└── logs/
    └── chat.log
```

### 7.2 索引文件
```json
{
  "sessions": [
    {
      "id": "session_001",
      "className": "com.example.MainActivity",
      "filePath": "/path/to/MainActivity.java",
      "createdAt": 1692345600000,
      "lastActiveAt": 1692349200000,
      "messageCount": 5
    }
  ],
  "totalSessions": 1,
  "lastCleanup": 1692345600000
}
```

## 8. 用户需求分析

### 8.1 核心需求
1. **API Key配置问题**：当前代码对话功能没有配置API Key，需要修复
2. **功能完善**：问答助手功能还没做好，需要深度解释和多轮问答
3. **PSI深度分析**：针对框选内容分析，必要时分析当前类的其他未框选代码
4. **调用链分析**：分析调用链路的其他类的方法或代码
5. **系统提示词配置**：支持后台设置配置系统提示词
6. **首次自动问答**：刚唤起时默认进行一次问答
7. **会话管理**：基于类全限定路径的会话管理
8. **历史记录**：本地保存会话和历史记录

### 8.2 会话管理需求
- **会话标识**：当前类的全限定路径对应一个会话
- **会话范围**：当前类任意框选的代码进行问答都在同一个会话里面
- **会话限制**：默认保留最近的100个会话（100个文件发起的会话）
- **消息限制**：默认单次会话保留50次问答
- **配置支持**：支持后台设置配置会话和消息数量限制

### 8.3 功能定位
代码对话助手相当于：
- **深度解释**：针对框选代码的深度解释
- **多轮问答**：多轮问答形式的深度解释
- **智能分析**：结合PSI获取的上下文信息进行智能分析

## 9. 实现计划

### 9.1 第一阶段：修复基础问题
- [x] 分析现有代码结构
- [ ] 修复API Key配置问题
- [ ] 完善PSI上下文分析器
- [ ] 实现基于类全限定路径的会话管理

### 9.2 第二阶段：核心功能实现
- [ ] 实现首次启动自动分析
- [ ] 增强PSI分析功能（调用链、依赖分析）
- [ ] 完善系统提示词配置
- [ ] 实现会话历史管理

### 9.3 第三阶段：存储和配置
- [ ] 实现会话持久化存储
- [ ] 添加配置管理功能（会话数量、消息数量限制）
- [ ] 实现会话清理和维护功能
- [ ] 优化配置UI界面

### 9.4 第四阶段：UI优化和测试
- [ ] 优化对话界面体验
- [ ] 添加会话历史面板
- [ ] 实现代码高亮显示
- [ ] 完善错误处理和用户指导

## 9. 技术难点

### 9.1 PSI分析复杂度
- **挑战**：准确分析调用链和依赖关系
- **解决方案**：使用IntelliJ PSI API的引用解析功能

### 9.2 上下文管理
- **挑战**：平衡上下文信息的完整性和AI token限制
- **解决方案**：智能裁剪和优先级排序

### 9.3 会话持久化性能
- **挑战**：大量会话数据的存储和检索性能
- **解决方案**：使用索引文件和延迟加载

### 9.4 内存管理
- **挑战**：长期运行时的内存占用
- **解决方案**：定期清理和弱引用管理

## 10. 扩展性考虑

### 10.1 多语言支持
- 设计通用的PSI分析接口
- 支持不同编程语言的特定分析器

### 10.2 AI模型切换
- 抽象AI服务接口
- 支持多种AI服务提供商

### 10.3 插件集成
- 提供API供其他插件集成
- 支持自定义分析器注册

---

此技术方案涵盖了代码问答助手的完整设计和实现计划。如有任何疑问或需要调整的地方，请随时提出。
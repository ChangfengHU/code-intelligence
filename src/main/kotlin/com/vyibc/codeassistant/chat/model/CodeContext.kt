package com.vyibc.codeassistant.chat.model

import com.intellij.openapi.util.TextRange

/**
 * 代码上下文信息
 */
data class CodeContext(
    val selectedCode: String,                      // 框选的代码
    val selectedRange: TextRange,                  // 选中范围
    val className: String,                         // 当前类名
    val methodName: String?,                       // 当前方法名
    val classContext: String,                      // 整个类的代码
    val imports: List<String>,                     // 导入语句
    val dependencies: List<DependencyInfo> = emptyList(), // 依赖信息
    val callChain: List<CallInfo> = emptyList()    // 调用链信息
)

/**
 * 依赖信息
 */
data class DependencyInfo(
    val className: String,                         // 依赖类名
    val methods: List<String>,                     // 相关方法
    val code: String                               // 相关代码片段
)

/**
 * 调用链信息
 */
data class CallInfo(
    val className: String,                         // 调用的类
    val methodName: String,                        // 调用的方法
    val code: String,                              // 方法代码
    val isExternal: Boolean = false                // 是否为外部调用
)
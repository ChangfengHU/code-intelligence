package com.vyibc.codeassistant.services

import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/**
 * 基于代码结构的智能批量处理器
 * 根据代码的PSI结构进行更合理的分批，而不是简单的行数分批
 */
class CodeStructureBatchProcessor {
    
    companion object {
        const val MAX_CHARS_PER_BATCH = 2000 // 每批最大字符数
        const val MIN_BATCH_SIZE = 3 // 最小批次大小（行数）
    }
    
    data class CodeBatch(
        val lines: List<String>,
        val startLineNumber: Int,
        val endLineNumber: Int,
        val type: BatchType,
        val description: String
    )
    
    enum class BatchType {
        IMPORT_BLOCK,      // 导入语句块
        CLASS_DEFINITION,  // 类定义
        METHOD_DEFINITION, // 方法定义  
        FUNCTION_BLOCK,    // 函数块
        COMMENT_BLOCK,     // 注释块
        DATA_STRUCTURE,    // 数据结构（如字典、列表等）
        MIXED_CODE         // 混合代码块
    }
    
    /**
     * 根据代码结构创建智能批次
     */
    fun createStructureBasedBatches(lines: List<String>, editor: Editor? = null): List<CodeBatch> {
        val batches = mutableListOf<CodeBatch>()
        
        // 首先尝试PSI分析
        if (editor != null) {
            val psiBatches = analyzePSIStructure(lines, editor)
            if (psiBatches.isNotEmpty()) {
                return psiBatches
            }
        }
        
        // PSI分析失败，使用基于文本的结构分析
        return analyzeTextStructure(lines)
    }
    
    /**
     * 基于PSI结构分析
     */
    private fun analyzePSIStructure(lines: List<String>, editor: Editor): List<CodeBatch> {
        val batches = mutableListOf<CodeBatch>()
        
        try {
            val psiFile = PsiDocumentManager.getInstance(editor.project ?: return emptyList())
                .getPsiFile(editor.document) ?: return emptyList()
            
            val selectionModel = editor.selectionModel
            val selectedText = selectionModel.selectedText ?: return emptyList()
            val startOffset = selectionModel.selectionStart
            
            // 找到选中区域对应的PSI元素
            val startElement = psiFile.findElementAt(startOffset)
            if (startElement != null) {
                // 分析PSI结构
                val structuralElements = findStructuralElements(startElement, lines.size)
                batches.addAll(createBatchesFromPSI(structuralElements, lines))
            }
            
        } catch (e: Exception) {
            // PSI分析失败，返回空列表，会回退到文本分析
            return emptyList()
        }
        
        return batches
    }
    
    /**
     * 查找结构化元素 - 使用通用方式
     */
    private fun findStructuralElements(startElement: PsiElement, maxLines: Int): List<Pair<PsiElement, String>> {
        val elements = mutableListOf<Pair<PsiElement, String>>()
        
        // 向上查找包含的结构化元素
        var current = startElement
        while (current.parent != null && elements.size < maxLines / MIN_BATCH_SIZE) {
            val elementName = current.javaClass.simpleName
            when {
                // 方法相关
                elementName.contains("Method", ignoreCase = true) -> {
                    val name = tryGetName(current) ?: "未知方法"
                    elements.add(current to "方法: $name")
                }
                // 类相关
                elementName.contains("Class", ignoreCase = true) -> {
                    val name = tryGetName(current) ?: "未知类"
                    elements.add(current to "类: $name")
                }
                // 导入相关
                elementName.contains("Import", ignoreCase = true) -> {
                    elements.add(current to "导入语句块")
                }
                // 代码块相关
                elementName.contains("Block", ignoreCase = true) -> {
                    val parent = current.parent
                    val parentName = parent?.javaClass?.simpleName ?: ""
                    val description = when {
                        parentName.contains("Method", ignoreCase = true) -> {
                            val methodName = tryGetName(parent) ?: "未知方法"
                            "方法体: $methodName"
                        }
                        parentName.contains("Class", ignoreCase = true) -> "类体"
                        else -> "代码块"
                    }
                    elements.add(current to description)
                }
                // 函数相关
                elementName.contains("Function", ignoreCase = true) -> {
                    val name = tryGetName(current) ?: "未知函数"
                    elements.add(current to "函数: $name")
                }
            }
            current = current.parent
        }
        
        return elements.distinctBy { it.second } // 去重
    }
    
    /**
     * 尝试获取PSI元素的名称（通过反射）
     */
    private fun tryGetName(element: PsiElement): String? {
        return try {
            // 尝试通过反射调用getName方法
            val method = element.javaClass.getMethod("getName")
            method.invoke(element) as? String
        } catch (e: Exception) {
            // 如果失败，尝试从text中提取
            val text = element.text
            when {
                text.startsWith("def ") -> text.substringAfter("def ").substringBefore("(").trim()
                text.startsWith("function ") -> text.substringAfter("function ").substringBefore("(").trim()
                text.startsWith("class ") -> text.substringAfter("class ").substringBefore(" ").substringBefore("{").trim()
                else -> null
            }
        }
    }
    
    /**
     * 从PSI元素创建批次
     */
    private fun createBatchesFromPSI(elements: List<Pair<PsiElement, String>>, lines: List<String>): List<CodeBatch> {
        val batches = mutableListOf<CodeBatch>()
        
        if (elements.isEmpty()) {
            return analyzeTextStructure(lines)
        }
        
        // 基于PSI元素的边界创建批次
        var currentStart = 0
        for ((element, description) in elements) {
            val elementLines = element.text.split("\n")
            val batchSize = minOf(elementLines.size, lines.size - currentStart)
            
            if (batchSize > 0 && currentStart < lines.size) {
                val batchLines = lines.subList(currentStart, minOf(currentStart + batchSize, lines.size))
                val batchType = determineBatchType(element)
                
                batches.add(CodeBatch(
                    lines = batchLines,
                    startLineNumber = currentStart,
                    endLineNumber = currentStart + batchSize - 1,
                    type = batchType,
                    description = description
                ))
                
                currentStart += batchSize
            }
        }
        
        // 处理剩余行
        if (currentStart < lines.size) {
            val remainingLines = lines.subList(currentStart, lines.size)
            batches.add(CodeBatch(
                lines = remainingLines,
                startLineNumber = currentStart,
                endLineNumber = lines.size - 1,
                type = BatchType.MIXED_CODE,
                description = "剩余代码"
            ))
        }
        
        return batches
    }
    
    /**
     * 确定PSI元素对应的批次类型 - 使用通用方式
     */
    private fun determineBatchType(element: PsiElement): BatchType {
        val elementName = element.javaClass.simpleName
        return when {
            elementName.contains("Import", ignoreCase = true) -> BatchType.IMPORT_BLOCK
            elementName.contains("Class", ignoreCase = true) -> BatchType.CLASS_DEFINITION
            elementName.contains("Method", ignoreCase = true) -> BatchType.METHOD_DEFINITION
            elementName.contains("Function", ignoreCase = true) -> BatchType.METHOD_DEFINITION
            elementName.contains("Block", ignoreCase = true) -> {
                val parent = element.parent
                val parentName = parent?.javaClass?.simpleName ?: ""
                when {
                    parentName.contains("Method", ignoreCase = true) -> BatchType.FUNCTION_BLOCK
                    parentName.contains("Function", ignoreCase = true) -> BatchType.FUNCTION_BLOCK
                    else -> BatchType.MIXED_CODE
                }
            }
            else -> BatchType.MIXED_CODE
        }
    }
    
    /**
     * 基于文本结构分析（当PSI不可用时的回退方案）
     */
    private fun analyzeTextStructure(lines: List<String>): List<CodeBatch> {
        val batches = mutableListOf<CodeBatch>()
        var currentBatch = mutableListOf<String>()
        var currentBatchStart = 0
        var currentBatchChars = 0
        var currentBatchType = BatchType.MIXED_CODE
        
        for ((index, line) in lines.withIndex()) {
            val lineType = detectLineType(line)
            val lineLength = line.length
            
            // 检查是否需要开始新批次
            val shouldStartNewBatch = shouldStartNewBatch(
                currentBatch, currentBatchChars, lineLength, currentBatchType, lineType
            )
            
            if (shouldStartNewBatch && currentBatch.isNotEmpty()) {
                // 结束当前批次
                batches.add(CodeBatch(
                    lines = currentBatch.toList(),
                    startLineNumber = currentBatchStart,
                    endLineNumber = currentBatchStart + currentBatch.size - 1,
                    type = currentBatchType,
                    description = generateBatchDescription(currentBatchType, currentBatch.size)
                ))
                
                // 开始新批次
                currentBatch.clear()
                currentBatchStart = index
                currentBatchChars = 0
                currentBatchType = lineType
            }
            
            currentBatch.add(line)
            currentBatchChars += lineLength
            
            // 如果当前是第一行，设置批次类型
            if (currentBatch.size == 1) {
                currentBatchType = lineType
            }
        }
        
        // 添加最后一个批次
        if (currentBatch.isNotEmpty()) {
            batches.add(CodeBatch(
                lines = currentBatch.toList(),
                startLineNumber = currentBatchStart,
                endLineNumber = currentBatchStart + currentBatch.size - 1,
                type = currentBatchType,
                description = generateBatchDescription(currentBatchType, currentBatch.size)
            ))
        }
        
        return batches
    }
    
    /**
     * 检测行的类型
     */
    private fun detectLineType(line: String): BatchType {
        val trimmed = line.trim()
        
        return when {
            // 导入语句
            trimmed.startsWith("import ") || 
            trimmed.startsWith("from ") ||
            trimmed.startsWith("#include") ||
            trimmed.startsWith("using ") ||
            trimmed.matches(Regex("^import\\s+\\{.*")) -> BatchType.IMPORT_BLOCK
            
            // 类定义
            trimmed.startsWith("class ") ||
            trimmed.startsWith("interface ") ||
            trimmed.startsWith("enum ") -> BatchType.CLASS_DEFINITION
            
            // 方法/函数定义
            trimmed.startsWith("def ") ||
            trimmed.startsWith("function ") ||
            trimmed.matches(Regex("^(public|private|protected)?\\s*(static\\s+)?\\w+\\s+\\w+\\s*\\(.*")) ||
            trimmed.startsWith("async def ") -> BatchType.METHOD_DEFINITION
            
            // 注释块
            trimmed.startsWith("//") ||
            trimmed.startsWith("#") ||
            trimmed.startsWith("/*") ||
            trimmed.startsWith("*") ||
            trimmed.startsWith("\"\"\"") ||
            trimmed.startsWith("'''") -> BatchType.COMMENT_BLOCK
            
            // 数据结构
            trimmed.contains("[") && (trimmed.contains("{") || trimmed.contains("\"")) ||
            trimmed.startsWith("{") ||
            trimmed.contains("=") && (trimmed.contains("[") || trimmed.contains("{")) -> BatchType.DATA_STRUCTURE
            
            else -> BatchType.MIXED_CODE
        }
    }
    
    /**
     * 判断是否需要开始新批次
     */
    private fun shouldStartNewBatch(
        currentBatch: List<String>,
        currentBatchChars: Int,
        lineLength: Int,
        currentType: BatchType,
        lineType: BatchType
    ): Boolean {
        // 如果当前批次为空，不需要新批次
        if (currentBatch.isEmpty()) return false
        
        // 字符数超限
        if (currentBatchChars + lineLength > MAX_CHARS_PER_BATCH) return true
        
        // 类型变化且当前批次已有最小行数
        if (currentType != lineType && currentBatch.size >= MIN_BATCH_SIZE) {
            // 特殊情况：注释和代码可以混合
            if (!((currentType == BatchType.COMMENT_BLOCK && lineType == BatchType.MIXED_CODE) ||
                  (currentType == BatchType.MIXED_CODE && lineType == BatchType.COMMENT_BLOCK))) {
                return true
            }
        }
        
        // 导入语句块单独成批
        if (currentType == BatchType.IMPORT_BLOCK && lineType != BatchType.IMPORT_BLOCK) return true
        if (currentType != BatchType.IMPORT_BLOCK && lineType == BatchType.IMPORT_BLOCK) return true
        
        // 类定义和方法定义倾向于单独成批
        if (currentType != lineType && 
            (lineType == BatchType.CLASS_DEFINITION || lineType == BatchType.METHOD_DEFINITION)) {
            return currentBatch.size >= MIN_BATCH_SIZE
        }
        
        return false
    }
    
    /**
     * 生成批次描述
     */
    private fun generateBatchDescription(type: BatchType, size: Int): String {
        return when (type) {
            BatchType.IMPORT_BLOCK -> "导入语句块 ($size 行)"
            BatchType.CLASS_DEFINITION -> "类定义 ($size 行)"
            BatchType.METHOD_DEFINITION -> "方法定义 ($size 行)"
            BatchType.FUNCTION_BLOCK -> "函数体 ($size 行)"
            BatchType.COMMENT_BLOCK -> "注释块 ($size 行)"
            BatchType.DATA_STRUCTURE -> "数据结构 ($size 行)"
            BatchType.MIXED_CODE -> "混合代码 ($size 行)"
        }
    }
}
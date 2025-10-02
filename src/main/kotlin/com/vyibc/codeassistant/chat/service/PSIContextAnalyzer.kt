package com.vyibc.codeassistant.chat.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.vyibc.codeassistant.chat.model.CodeContext

/**
 * 通用 PSI 上下文分析器，避免直接依赖 Java 专用 PSI。
 */
@Service
class PSIContextAnalyzer {

    fun analyzeContext(file: PsiFile, selectionRange: TextRange): CodeContext {
        val safeRange = clampRange(selectionRange, file.textLength)
        val selectedCode = extractSelectedCode(file, safeRange)
        val elementOffset = calculateElementOffset(safeRange.startOffset, file.textLength)
        val selectedElement = file.findElementAt(elementOffset)

        val methodOwner = findNearestNameOwner(selectedElement)
        val classOwner = methodOwner?.let { findNearestNameOwner(it.parent) }
            ?: findNearestNameOwner(selectedElement?.parent)

        val className = (classOwner?.name).orElse(defaultName(file))
        val methodName = methodOwner?.name
        val classContext = limitContextText(classOwner?.text ?: file.text)
        val imports = extractImportsFallback(file)

        return CodeContext(
            selectedCode = selectedCode,
            selectedRange = safeRange,
            className = className,
            methodName = methodName,
            classContext = classContext,
            imports = imports
        )
    }

    private fun clampRange(range: TextRange, textLength: Int): TextRange {
        val start = range.startOffset.coerceIn(0, textLength)
        val end = range.endOffset.coerceIn(start, textLength)
        return TextRange(start, end)
    }

    private fun calculateElementOffset(start: Int, textLength: Int): Int {
        if (textLength == 0) {
            return 0
        }
        return start.coerceIn(0, textLength - 1)
    }

    private fun extractSelectedCode(file: PsiFile, range: TextRange): String {
        if (range.isEmpty || file.textLength == 0) {
            return ""
        }
        val end = range.endOffset.coerceAtMost(file.textLength)
        return file.text.substring(range.startOffset, end)
    }

    private fun findNearestNameOwner(element: PsiElement?): PsiNameIdentifierOwner? {
        if (element == null) {
            return null
        }
        return when (element) {
            is PsiNameIdentifierOwner -> element
            else -> PsiTreeUtil.getParentOfType(element, PsiNameIdentifierOwner::class.java, false)
        }
    }

    private fun limitContextText(text: String): String {
        if (text.length <= 3000) {
            return text
        }
        return text.take(3000) + "\n... (上下文过长，已截断)"
    }

    private fun defaultName(file: PsiFile): String {
        val virtual = file.virtualFile
        if (virtual != null) {
            val nameWithoutExtension = virtual.nameWithoutExtension
            if (nameWithoutExtension.isNotBlank()) {
                return nameWithoutExtension
            }
        }
        return file.name.substringBeforeLast('.')
    }

    private fun extractImportsFallback(file: PsiFile): List<String> {
        val lines = file.text.lineSequence().map { it.trim() }
        val imports = mutableListOf<String>()
        for (line in lines) {
            if (line.isBlank()) {
                continue
            }
            if (isImportLine(line)) {
                imports.add(line)
            }
            if (!line.startsWith("import") && !line.startsWith("from")) {
                if (imports.isNotEmpty()) {
                    break
                }
            }
            if (imports.size >= 20) {
                break
            }
        }
        return imports
    }

    private fun isImportLine(line: String): Boolean {
        return line.startsWith("import ") ||
            line.startsWith("from ") ||
            line.startsWith("using ") ||
            line.startsWith("#include") ||
            line.startsWith("require(")
    }

    private fun String?.orElse(fallback: String): String {
        return if (this.isNullOrBlank()) fallback else this
    }

    companion object {
        fun getInstance(): PSIContextAnalyzer = service()
    }
}

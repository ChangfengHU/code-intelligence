package com.vyibc.codeassistant.chat.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.vyibc.codeassistant.chat.model.CallInfo
import com.vyibc.codeassistant.chat.model.CodeContext
import com.vyibc.codeassistant.chat.model.DependencyInfo

/**
 * PSI代码上下文分析器
 */
@Service
class PSIContextAnalyzer {
    
    /**
     * 分析代码上下文
     */
    fun analyzeContext(file: PsiFile, selectionRange: TextRange): CodeContext {
        val selectedCode = file.text.substring(selectionRange.startOffset, selectionRange.endOffset)
        val selectedElement = file.findElementAt(selectionRange.startOffset)
        
        // 找到包含的类
        val psiClass = PsiTreeUtil.getParentOfType(selectedElement, PsiClass::class.java)
        val className = psiClass?.qualifiedName ?: "Unknown"
        
        // 找到包含的方法
        val psiMethod = PsiTreeUtil.getParentOfType(selectedElement, PsiMethod::class.java)
        val methodName = psiMethod?.name
        
        // 获取整个类的代码
        val classContext = psiClass?.text ?: file.text
        
        // 获取导入语句
        val imports = extractImports(file)
        
        // 分析依赖（简化版本）
        val dependencies = if (psiClass != null) findDependencies(psiClass) else emptyList()
        
        // 分析调用链（简化版本）
        val callChain = if (selectedElement != null) findCallChain(selectedElement) else emptyList()
        
        return CodeContext(
            selectedCode = selectedCode,
            selectedRange = selectionRange,
            className = className,
            methodName = methodName,
            classContext = classContext,
            imports = imports,
            dependencies = dependencies,
            callChain = callChain
        )
    }
    
    /**
     * 提取导入语句
     */
    private fun extractImports(file: PsiFile): List<String> {
        return when (file) {
            is PsiJavaFile -> {
                file.importList?.importStatements?.map { it.text } ?: emptyList()
            }
            else -> emptyList()
        }
    }
    
    /**
     * 查找依赖信息（简化实现）
     */
    private fun findDependencies(psiClass: PsiClass): List<DependencyInfo> {
        val dependencies = mutableListOf<DependencyInfo>()
        
        // 分析字段类型
        psiClass.fields.forEach { field: PsiField ->
            val fieldType = field.type
            if (fieldType is PsiClassType) {
                val referencedClass = fieldType.resolve()
                if (referencedClass != null && !isJavaLangClass(referencedClass)) {
                    val methods = referencedClass.methods.take(3).map { it.name }
                    dependencies.add(DependencyInfo(
                        className = referencedClass.qualifiedName ?: "Unknown",
                        methods = methods,
                        code = referencedClass.text.take(500) + if (referencedClass.text.length > 500) "..." else ""
                    ))
                }
            }
        }
        
        return dependencies.take(3) // 限制依赖数量
    }
    
    /**
     * 查找调用链（简化实现）
     */
    private fun findCallChain(element: PsiElement): List<CallInfo> {
        val callInfos = mutableListOf<CallInfo>()
        
        // 查找方法调用
        val methodCalls = PsiTreeUtil.findChildrenOfType(element.parent, PsiMethodCallExpression::class.java)
        methodCalls.take(5).forEach { call: PsiMethodCallExpression ->
            val method = call.resolveMethod()
            if (method != null) {
                val containingClass = method.containingClass
                if (containingClass != null && !isJavaLangClass(containingClass)) {
                    callInfos.add(CallInfo(
                        className = containingClass.qualifiedName ?: "Unknown",
                        methodName = method.name,
                        code = method.text.take(300) + if (method.text.length > 300) "..." else "",
                        isExternal = !isProjectClass(containingClass)
                    ))
                }
            }
        }
        
        return callInfos
    }
    
    /**
     * 检查是否为Java标准库类
     */
    private fun isJavaLangClass(psiClass: PsiClass): Boolean {
        val qualifiedName = psiClass.qualifiedName ?: return false
        return (qualifiedName as String).startsWith("java.") || 
               (qualifiedName as String).startsWith("javax.") ||
               (qualifiedName as String).startsWith("kotlin.")
    }
    
    /**
     * 检查是否为项目内的类（简化判断）
     */
    private fun isProjectClass(psiClass: PsiClass): Boolean {
        return psiClass.containingFile?.virtualFile?.path?.contains("/src/") == true
    }
    
    companion object {
        fun getInstance(): PSIContextAnalyzer = service()
    }
}
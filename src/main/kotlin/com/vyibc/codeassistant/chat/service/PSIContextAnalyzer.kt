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
        val className = psiClass?.qualifiedName ?: extractClassNameFromFile(file)
        
        // 找到包含的方法
        val psiMethod = PsiTreeUtil.getParentOfType(selectedElement, PsiMethod::class.java)
        val methodName = psiMethod?.name
        
        // 获取整个类的代码（限制长度避免token过多）
        val classContext = psiClass?.text ?: file.text
        val limitedClassContext = if (classContext.length > 3000) {
            classContext.take(3000) + "\n... (类内容过长，已截断)"
        } else {
            classContext
        }
        
        // 获取导入语句
        val imports = extractImports(file)
        
        // 分析依赖（增强版本）
        val dependencies = if (psiClass != null) findDependencies(psiClass) else emptyList()
        
        // 分析调用链（增强版本）
        val callChain = if (selectedElement != null) findCallChain(selectedElement) else emptyList()
        
        return CodeContext(
            selectedCode = selectedCode,
            selectedRange = selectionRange,
            className = className,
            methodName = methodName,
            classContext = limitedClassContext,
            imports = imports,
            dependencies = dependencies,
            callChain = callChain
        )
    }
    
    /**
     * 从文件中提取类名（支持多种语言）
     */
    private fun extractClassNameFromFile(file: PsiFile): String {
        val fileName = file.name
        val extension = fileName.substringAfterLast('.', "")
        val baseName = fileName.substringBeforeLast('.')
        
        val fileText = file.text
        
        return when (extension) {
            "java" -> {
                // Java: public class ClassName
                Regex("public\\s+class\\s+(\\w+)").find(fileText)?.groupValues?.get(1) ?: baseName
            }
            "kt" -> {
                // Kotlin: class ClassName
                Regex("class\\s+(\\w+)").find(fileText)?.groupValues?.get(1) ?: baseName
            }
            "py" -> {
                // Python: class ClassName:
                Regex("class\\s+(\\w+)\\s*[:(]").find(fileText)?.groupValues?.get(1) ?: baseName
            }
            "js", "ts" -> {
                // JavaScript/TypeScript: class ClassName 或 function ClassName
                val classMatch = Regex("class\\s+(\\w+)").find(fileText)
                val functionMatch = Regex("function\\s+(\\w+)").find(fileText)
                classMatch?.groupValues?.get(1) ?: functionMatch?.groupValues?.get(1) ?: baseName
            }
            "go" -> {
                // Go: type StructName struct 或 func FuncName
                val structMatch = Regex("type\\s+(\\w+)\\s+struct").find(fileText)
                val funcMatch = Regex("func\\s+(\\w+)").find(fileText)
                structMatch?.groupValues?.get(1) ?: funcMatch?.groupValues?.get(1) ?: baseName
            }
            "cpp", "c", "h", "hpp" -> {
                // C++: class ClassName 或 struct ClassName
                val classMatch = Regex("class\\s+(\\w+)").find(fileText)
                val structMatch = Regex("struct\\s+(\\w+)").find(fileText)
                classMatch?.groupValues?.get(1) ?: structMatch?.groupValues?.get(1) ?: baseName
            }
            else -> baseName
        }
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
     * 查找依赖信息（增强实现）
     */
    private fun findDependencies(psiClass: PsiClass): List<DependencyInfo> {
        val dependencies = mutableListOf<DependencyInfo>()
        val processedClasses = mutableSetOf<String>()
        
        // 分析字段类型
        psiClass.fields.forEach { field: PsiField ->
            val fieldType = field.type
            if (fieldType is PsiClassType) {
                val referencedClass = fieldType.resolve()
                if (referencedClass != null && !isJavaLangClass(referencedClass) && 
                    !processedClasses.contains(referencedClass.qualifiedName)) {
                    
                    processedClasses.add(referencedClass.qualifiedName ?: "")
                    
                    val methods = referencedClass.methods.take(5).map { it.name }
                    val classCode = referencedClass.text.take(800)
                    
                    dependencies.add(DependencyInfo(
                        className = referencedClass.qualifiedName ?: "Unknown",
                        methods = methods,
                        code = classCode + if (referencedClass.text.length > 800) "..." else ""
                    ))
                }
            }
        }
        
        // 分析方法参数和返回值类型
        psiClass.methods.forEach { method: PsiMethod ->
            // 分析参数类型
            method.parameterList.parameters.forEach { param: PsiParameter ->
                val paramType = param.type
                if (paramType is PsiClassType) {
                    val referencedClass = paramType.resolve()
                    if (referencedClass != null && !isJavaLangClass(referencedClass) && 
                        !processedClasses.contains(referencedClass.qualifiedName)) {
                        
                        processedClasses.add(referencedClass.qualifiedName ?: "")
                        
                        val methods = referencedClass.methods.take(3).map { it.name }
                        val classCode = referencedClass.text.take(600)
                        
                        dependencies.add(DependencyInfo(
                            className = referencedClass.qualifiedName ?: "Unknown",
                            methods = methods,
                            code = classCode + if (referencedClass.text.length > 600) "..." else ""
                        ))
                    }
                }
            }
            
            // 分析返回值类型
            val returnType = method.returnType
            if (returnType is PsiClassType) {
                val referencedClass = returnType.resolve()
                if (referencedClass != null && !isJavaLangClass(referencedClass) && 
                    !processedClasses.contains(referencedClass.qualifiedName)) {
                    
                    processedClasses.add(referencedClass.qualifiedName ?: "")
                    
                    val methods = referencedClass.methods.take(3).map { it.name }
                    val classCode = referencedClass.text.take(600)
                    
                    dependencies.add(DependencyInfo(
                        className = referencedClass.qualifiedName ?: "Unknown",
                        methods = methods,
                        code = classCode + if (referencedClass.text.length > 600) "..." else ""
                    ))
                }
            }
        }
        
        return dependencies.take(5) // 限制依赖数量
    }
    
    /**
     * 查找调用链（增强实现）
     */
    private fun findCallChain(element: PsiElement): List<CallInfo> {
        val callInfos = mutableListOf<CallInfo>()
        val processedMethods = mutableSetOf<String>()
        
        // 查找选中代码中的方法调用
        val selectedMethodCalls = PsiTreeUtil.findChildrenOfType(element, PsiMethodCallExpression::class.java)
        selectedMethodCalls.forEach { call: PsiMethodCallExpression ->
            val method = call.resolveMethod()
            if (method != null) {
                val containingClass = method.containingClass
                if (containingClass != null && !isJavaLangClass(containingClass)) {
                    val methodKey = "${containingClass.qualifiedName}.${method.name}"
                    if (!processedMethods.contains(methodKey)) {
                        processedMethods.add(methodKey)
                        
                        callInfos.add(CallInfo(
                            className = containingClass.qualifiedName ?: "Unknown",
                            methodName = method.name,
                            code = method.text.take(500) + if (method.text.length > 500) "..." else "",
                            isExternal = !isProjectClass(containingClass)
                        ))
                    }
                }
            }
        }
        
        // 查找父级元素中的方法调用（扩大搜索范围）
        var currentElement = element.parent
        var depth = 0
        while (currentElement != null && depth < 3) {
            val methodCalls = PsiTreeUtil.findChildrenOfType(currentElement, PsiMethodCallExpression::class.java)
            methodCalls.take(3).forEach { call: PsiMethodCallExpression ->
                val method = call.resolveMethod()
                if (method != null) {
                    val containingClass = method.containingClass
                    if (containingClass != null && !isJavaLangClass(containingClass)) {
                        val methodKey = "${containingClass.qualifiedName}.${method.name}"
                        if (!processedMethods.contains(methodKey)) {
                            processedMethods.add(methodKey)
                            
                            callInfos.add(CallInfo(
                                className = containingClass.qualifiedName ?: "Unknown",
                                methodName = method.name,
                                code = method.text.take(400) + if (method.text.length > 400) "..." else "",
                                isExternal = !isProjectClass(containingClass)
                            ))
                        }
                    }
                }
            }
            currentElement = currentElement.parent
            depth++
        }
        
        // 查找构造函数调用
        val constructorCalls = PsiTreeUtil.findChildrenOfType(element, PsiNewExpression::class.java)
        constructorCalls.take(3).forEach { newExpr: PsiNewExpression ->
            val classType = newExpr.classReference?.resolve()
            if (classType is PsiClass && !isJavaLangClass(classType)) {
                val classKey = "${classType.qualifiedName}.<init>"
                if (!processedMethods.contains(classKey)) {
                    processedMethods.add(classKey)
                    
                    callInfos.add(CallInfo(
                        className = classType.qualifiedName ?: "Unknown",
                        methodName = "<init>",
                        code = classType.text.take(400) + if (classType.text.length > 400) "..." else "",
                        isExternal = !isProjectClass(classType)
                    ))
                }
            }
        }
        
        return callInfos.take(8) // 限制调用链数量
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
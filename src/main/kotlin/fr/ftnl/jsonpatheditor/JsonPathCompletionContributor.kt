package fr.ftnl.jsonpatheditor

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonElement
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiManager
import com.intellij.util.ProcessingContext
import fr.ftnl.jsonpatheditor.internal.JsonPathLogic

class JsonPathCompletionContributor : CompletionContributor() {
    
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val project = parameters.position.project
                    val editorManager = FileEditorManager.getInstance(project)
                    
                    val virtualFile = editorManager.selectedFiles.firstOrNull {
                        it.name.endsWith(".json", true) ||
                                it.name.endsWith(".jsonc", true) ||
                                it.name.endsWith(".json5", true)
                    } ?: editorManager.selectedFiles.firstOrNull() ?: return
                    
                    val jsonFile = PsiManager.getInstance(project).findFile(virtualFile) as? JsonFile ?: return
                    val documentText = jsonFile.text
                    
                    val toolWindowEditor = parameters.editor
                    val text = toolWindowEditor.document.getText(com.intellij.openapi.util.TextRange(0, parameters.offset))
                    
                    // ==========================================
                    // 1. NOUVEAU : DÉTECTION DES OPÉRATEURS
                    // ==========================================
                    // Si on est dans un filtre, après une propriété et un espace, on propose les opérateurs
                    val operatorRegex = """(?s).*\[\?\([^)]*(?:@\.[a-zA-Z0-9_-]+|@\['[^']+'\]|@\["[^"]+"\]|@)\s+([a-zA-Z!<=>~]*)$""".toRegex()
                    val operatorMatch = operatorRegex.find(text)
                    
                    if (operatorMatch != null) {
                        val prefix = operatorMatch.groupValues[1]
                        val r = result.withPrefixMatcher(prefix)
                        
                        // Liste officielle des opérateurs supportés par Jayway
                        val operators = listOf(
                            "==", "!=", "<", "<=", ">", ">=", "=~",
                            "in", "nin", "subsetof", "anyof", "noneof", "size", "empty", "contains"
                        )
                        
                        operators.forEach { op ->
                            r.addElement(
                                LookupElementBuilder.create(op)
                                    .withIcon(AllIcons.Nodes.Function) // Icône distincte (fonction/opération)
                                    .withTypeText("Operator")
                                    .withBoldness(true) // En gras pour bien les repérer
                            )
                        }
                        return // On s'arrête là, pas besoin de chercher des clés JSON !
                    }
                    
                    // ==========================================
                    // 2. DÉCOUPAGE MAGIQUE (Clés et Deep Scan)
                    // ==========================================
                    val regex = """(?s)^(.*?)(\.\.|\.|\[\s*['"]|@\.)([a-zA-Z0-9_-]*)$""".toRegex()
                    val match = regex.find(text)
                    
                    if (match == null) {
                        if (text == "$") {
                            val r = result.withPrefixMatcher("")
                            jsonFile.topLevelValue?.let { root ->
                                val suggestedKeys = mutableSetOf<String>()
                                extractKeys(root, r, suggestedKeys, deepScan = false)
                            }
                        }
                        return
                    }
                    
                    val rawBasePath = match.groupValues[1]
                    val separator = match.groupValues[2]
                    val prefix = match.groupValues[3]
                    val r = result.withPrefixMatcher(prefix)
                    
                    var evaluablePath = ""
                    var deepScan = false
                    
                    if (separator == "@.") {
                        val filterStart = rawBasePath.lastIndexOf("[?(")
                        evaluablePath = if (filterStart >= 0) {
                            rawBasePath.substring(0, filterStart) + "[*]"
                        } else "$"
                    } else if (separator == "..") {
                        evaluablePath = rawBasePath.ifEmpty { "$" }
                        deepScan = true
                    } else {
                        evaluablePath = rawBasePath.ifEmpty { "$" }
                    }
                    
                    try {
                        val paths = JsonPathLogic.getPaths(documentText, evaluablePath)
                        val suggestedKeys = mutableSetOf<String>()
                        
                        for (path in paths) {
                            val element = JsonPathLogic.findPsiElement(jsonFile, path)
                            if (element != null) {
                                extractKeys(element, r, suggestedKeys, deepScan)
                            }
                        }
                        
                        if (!deepScan && suggestedKeys.isNotEmpty() && suggestedKeys.add("*")) {
                            r.addElement(
                                LookupElementBuilder.create("*")
                                    .withIcon(AllIcons.Nodes.Parameter)
                                    .withTypeText("Wildcard")
                            )
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        )
    }
    
    private fun extractKeys(
        element: JsonElement,
        result: CompletionResultSet,
        suggestedKeys: MutableSet<String>,
        deepScan: Boolean
    ) {
        if (element is JsonArray) {
            element.valueList.forEach { extractKeys(it, result, suggestedKeys, deepScan) }
        } else if (element is JsonObject) {
            element.propertyList.forEach { prop ->
                if (suggestedKeys.add(prop.name)) {
                    result.addElement(
                        LookupElementBuilder.create(prop.name)
                            .withIcon(AllIcons.Nodes.Property)
                            .withTypeText("Clé JSON")
                    )
                }
                if (deepScan && prop.value != null) {
                    extractKeys(prop.value!!, result, suggestedKeys, deepScan = true)
                }
            }
        }
    }
}
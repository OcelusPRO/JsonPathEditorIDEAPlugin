package fr.ftnl.jsonpatheditor

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.json.psi.JsonArray
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
                    
                    // 1. Isoler le préfixe tapé après le dernier point
                    val lastDotIndex = text.lastIndexOf('.')
                    if (lastDotIndex == -1) {
                        if (text == "$") {
                            // On désactive le filtre pour le premier caractère
                            val r = result.withPrefixMatcher("")
                            suggestKeysFromRoot(jsonFile, r)
                        }
                        return
                    }
                    
                    val prefix = text.substring(lastDotIndex + 1)
                    val rawPath = text.substring(0, lastDotIndex)
                    
                    // 2. CORRECTION : On redonne à IntelliJ le vrai préfixe pour qu'il filtre les résultats
                    val r = result.withPrefixMatcher(prefix)
                    
                    // 3. Réparation du JsonPath (Filtres non fermés)
                    val lastOpenBracket = rawPath.lastIndexOf("[?(")
                    val lastCloseBracket = rawPath.lastIndexOf("]")
                    
                    val evaluablePath: String = if (lastOpenBracket > lastCloseBracket) {
                        // On est DANS un filtre [?( ... )] qui n'est pas encore fermé
                        val basePath = rawPath.substring(0, lastOpenBracket)
                        val lastAtIndex = rawPath.lastIndexOf('@')
                        
                        // On récupère ce qu'il y a après le @ (ex: ".books.*")
                        val relativePath = if (lastAtIndex != -1 && lastAtIndex < rawPath.length) {
                            rawPath.substring(lastAtIndex + 1)
                        } else {
                            ""
                        }
                        // On fusionne avec [*] pour évaluer le chemin parent
                        "$basePath[*]$relativePath"
                    } else {
                        // Chemin normal
                        if (rawPath.isEmpty()) "$" else rawPath
                    }
                    
                    // 4. Évaluation et suggestions
                    try {
                        val paths = JsonPathLogic.getPaths(documentText, evaluablePath)
                        val suggestedKeys = mutableSetOf<String>()
                        
                        // AJOUT : Proposer l'étoile * si on a des résultats
                        if (paths.isNotEmpty() && suggestedKeys.add("*")) {
                            r.addElement(
                                LookupElementBuilder.create("*")
                                    .withIcon(AllIcons.Nodes.Tag) // Icône bleue différente
                                    .withTypeText("Wildcard")
                            )
                        }
                        
                        for (path in paths) {
                            val element = JsonPathLogic.findPsiElement(jsonFile, path)
                            
                            // Si on tombe sur un tableau, on propose les clés des objets à l'intérieur
                            if (element is JsonArray) {
                                element.valueList.forEach { item ->
                                    if (item is JsonObject) {
                                        item.propertyList.forEach { prop ->
                                            if (suggestedKeys.add(prop.name)) {
                                                r.addElement(createLookupElement(prop.name))
                                            }
                                        }
                                    }
                                }
                            } else if (element is JsonObject) {
                                element.propertyList.forEach { prop ->
                                    if (suggestedKeys.add(prop.name)) {
                                        r.addElement(createLookupElement(prop.name))
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Ignorer les erreurs d'évaluation silencieusement
                    }
                }
            }
        )
    }
    
    private fun suggestKeysFromRoot(jsonFile: JsonFile, result: CompletionResultSet) {
        val topLevel = jsonFile.topLevelValue
        if (topLevel is JsonObject) {
            topLevel.propertyList.forEach { property ->
                result.addElement(createLookupElement(property.name))
            }
        }
    }
    
    private fun createLookupElement(name: String): LookupElementBuilder {
        return LookupElementBuilder.create(name)
            .withIcon(AllIcons.Nodes.Property)
            .withTypeText("Clé JSON")
    }
}
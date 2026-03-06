package fr.ftnl.jsonpatheditor

import com.intellij.json.psi.JsonFile
import com.intellij.lang.Language
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiManager
import com.intellij.ui.JBColor
import com.intellij.ui.LanguageTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBFont
import fr.ftnl.jsonpatheditor.internal.JsonPathLogic
import fr.ftnl.jsonpatheditor.internal.isPremiumActive
import java.awt.BorderLayout
import java.awt.Font
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JPanel
import com.intellij.openapi.editor.event.DocumentListener as OpenApiDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent as OpenApiDocumentEvent

class JsonPathToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowPanel = JsonPathToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(toolWindowPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class JsonPathToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val searchField: LanguageTextField
    private val resultsPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    
    init {
        val jsonPathLang = Language.findLanguageByID("JSONPath") ?: Language.findLanguageByID("TEXT")
        searchField = LanguageTextField(jsonPathLang, project, "$.")
        
        searchField.document.addDocumentListener(object : OpenApiDocumentListener {
            override fun documentChanged(event: OpenApiDocumentEvent) = evaluateAndRender()
        })
        
        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) = evaluateAndRender()
        })
        
        add(searchField, BorderLayout.NORTH)
        add(JBScrollPane(resultsPanel), BorderLayout.CENTER)
    }
    
    fun updateSearchQuery(newQuery: String) { searchField.text = newQuery }
    
    private fun evaluateAndRender() {
        resultsPanel.removeAll()
        val query = searchField.text
        if (query.isBlank() || query == "$.") return refreshWithMsg("Entrez une requête...")
        
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val virtualFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() ?: return
        val document = editor.document
        
        try {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? JsonFile ?: return
            val paths = JsonPathLogic.getPaths(document.text, query)
            
            if (paths.isEmpty()) {
                refreshWithMsg("Aucun résultat.")
            } else {
                val jsonLang = Language.findLanguageByID("JSON") ?: Language.findLanguageByID("TEXT")
                for (path in paths) {
                    val element = JsonPathLogic.findPsiElement(psiFile, path) ?: continue
                    val row = JPanel(BorderLayout()).apply { border = BorderFactory.createEmptyBorder(0, 0, 15, 0) }
                    
                    val editField = LanguageTextField(jsonLang, project, JsonPathLogic.formatPretty(element.text), false)
                    val marker = document.createRangeMarker(element.textRange)
                    
                    editField.addSettingsProvider { it.contentComponent.addFocusListener(object : FocusAdapter() {
                        override fun focusGained(e: FocusEvent?) {
                            if (marker.isValid) {
                                editor.caretModel.moveToOffset(marker.startOffset)
                                editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                            }
                        }
                    })}
                    
                    editField.document.addDocumentListener(object : OpenApiDocumentListener {
                        override fun documentChanged(event: OpenApiDocumentEvent) {
                            if (!marker.isValid) return
                            val lineStart = document.getLineStartOffset(document.getLineNumber(marker.startOffset))
                            val indent = document.getText(TextRange(lineStart, marker.startOffset)).takeWhile { it.isWhitespace() }
                            val newValue = JsonPathLogic.processValueForDoc(editField.text, indent)
                            
                            if (document.getText(TextRange(marker.startOffset, marker.endOffset)) != newValue) {
                                WriteCommandAction.runWriteCommandAction(project) {
                                    if (marker.isValid) document.replaceString(marker.startOffset, marker.endOffset, newValue)
                                }
                            }
                        }
                    })
                    row.add(JBLabel(path).apply { font = JBFont.create(Font("Monospaced", Font.BOLD, 12)); foreground = JBColor.BLUE }, BorderLayout.NORTH)
                    row.add(editField, BorderLayout.CENTER)
                    resultsPanel.add(row)
                }
            }
        } catch (e: Exception) { refreshWithMsg("Erreur de syntaxe.") }
        refreshUI()
    }
    
    private fun refreshWithMsg(msg: String) { resultsPanel.add(JBLabel(msg)); refreshUI() }
    private fun refreshUI() { resultsPanel.revalidate(); resultsPanel.repaint() }
}
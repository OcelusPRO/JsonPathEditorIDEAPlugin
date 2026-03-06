package fr.ftnl.jsonpatheditor.internal

import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonValue
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.util.PsiTreeUtil
import fr.ftnl.jsonpatheditor.JsonPathToolWindowPanel
import java.util.Base64

object ActionLogic {
    
    fun getPluginName(): String {
        return Base64.getDecoder().decode("UEpTT05QQVRI").toString(Charsets.UTF_8)
    }
    
    fun executeSearch(e: AnActionEvent, toolWindowId: String) {
        val project = e.project ?: return
        
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        
        val element = psiFile.findElementAt(editor.caretModel.offset)
        val jsonValue = PsiTreeUtil.getParentOfType(element, JsonValue::class.java) ?: return
        val jsonProperty = PsiTreeUtil.getParentOfType(jsonValue, JsonProperty::class.java) ?: return
        
        val query = "$..[?(@.${jsonProperty.name} == ${jsonValue.text})]"
        
        val toolWindow = ToolWindowManager.Companion.getInstance(project).getToolWindow(toolWindowId)
        toolWindow?.show {
            val component = toolWindow.contentManager.getContent(0)?.component as? JsonPathToolWindowPanel
            component?.updateSearchQuery(query)
        }
    }
    
    fun checkVisibility(e: AnActionEvent): Boolean {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return false
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return false
        val element = psiFile.findElementAt(editor.caretModel.offset) ?: return false
        
        return PsiTreeUtil.getParentOfType(element, JsonValue::class.java) != null &&
                PsiTreeUtil.getParentOfType(element, JsonProperty::class.java) != null
    }
}